package com.ibm.consulting.sim.shared.infrastructure.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Non-blocking Loki transport for environments where a log agent cannot tail
 * another container's stdout, such as Railway. Console JSON logging remains the
 * primary sink; this secondary sink drops under pressure rather than delaying a
 * learner request or recursively logging transport failures.
 */
public final class LokiHttpAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
    private static final int QUEUE_CAPACITY = 2_000;
    private static final ObjectMapper JSON = new ObjectMapper();

    private boolean enabled;
    private String pushUrl;
    private String serviceName = "consulting-simulation-api";
    private int batchSize = 100;
    private long flushIntervalMs = 1_000;
    private ArrayBlockingQueue<LokiEvent> queue;
    private ScheduledExecutorService sender;
    private HttpClient client;

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setPushUrl(String pushUrl) { this.pushUrl = pushUrl; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public void setFlushIntervalMs(long flushIntervalMs) { this.flushIntervalMs = flushIntervalMs; }

    @Override
    public void start() {
        if (!enabled) return;
        if (pushUrl == null || pushUrl.isBlank()) {
            addWarn("LOKI_ENABLED is true but LOKI_PUSH_URL is empty; Loki log shipping is disabled");
            return;
        }
        queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        sender = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "loki-log-sender");
            thread.setDaemon(true);
            return thread;
        });
        sender.scheduleWithFixedDelay(this::flushSafely, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted()) return;
        queue.offer(LokiEvent.from(event));
    }

    @Override
    public void stop() {
        if (!isStarted()) return;
        sender.shutdown();
        flushSafely();
        super.stop();
    }

    private void flushSafely() {
        try {
            flush();
        } catch (Exception ignored) {
            // Deliberately silent: logging transport must never create a log loop.
        }
    }

    private void flush() throws Exception {
        List<LokiEvent> events = new ArrayList<>(Math.max(1, batchSize));
        queue.drainTo(events, Math.max(1, batchSize));
        if (events.isEmpty()) return;

        List<List<String>> values = new ArrayList<>(events.size());
        for (LokiEvent event : events) {
            values.add(List.of(Long.toString(event.timestampMillis() * 1_000_000), JSON.writeValueAsString(event.line())));
        }
        Map<String, Object> stream = Map.of("stream", Map.of("service", safeServiceName()), "values", values);
        String payload = JSON.writeValueAsString(Map.of("streams", List.of(stream)));
        HttpRequest request = HttpRequest.newBuilder(URI.create(pushUrl))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        if (status < 200 || status >= 300) throw new IllegalStateException("Loki push returned " + status);
    }

    private String safeServiceName() {
        return serviceName == null || serviceName.isBlank() ? "consulting-simulation-api" : serviceName;
    }

    private record LokiEvent(long timestampMillis, Map<String, Object> line) {
        static LokiEvent from(ILoggingEvent event) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("level", event.getLevel().toString());
            line.put("logger", event.getLoggerName());
            line.put("message", event.getFormattedMessage());
            Map<String, String> mdc = event.getMDCPropertyMap();
            copyMdc(mdc, line, "requestId");
            copyMdc(mdc, line, "httpMethod");
            copyMdc(mdc, line, "httpPath");
            copyMdc(mdc, line, "userId");
            return new LokiEvent(event.getTimeStamp(), Map.copyOf(line));
        }

        private static void copyMdc(Map<String, String> mdc, Map<String, Object> target, String key) {
            if (mdc.containsKey(key)) target.put(key, mdc.get(key));
        }
    }
}
