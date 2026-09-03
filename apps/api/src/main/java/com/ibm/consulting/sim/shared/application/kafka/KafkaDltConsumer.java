package com.ibm.consulting.sim.shared.application.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Terminal observer for dead-letter records. Values remain opaque because an
 * unreadable payload is one of the primary reasons a record reaches the DLT.
 */
@Component
public class KafkaDltConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaDltConsumer.class);

    private final KafkaDltMetrics metrics;

    public KafkaDltConsumer(KafkaDltMetrics metrics) {
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = "${app.kafka.notifications.dlt.topic-name:notifications.DLT}",
            groupId = "${app.kafka.notifications.dlt.consumer.group-id:notification-dlt-monitor}",
            concurrency = "${app.kafka.notifications.dlt.consumer.concurrency:1}",
            containerFactory = "kafkaDltListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, byte[]> record) {
        // Never deserialize or log the payload. Malformed serialized data is a
        // valid DLT use case; metadata and headers remain safe to inspect.
        metrics.recordReceived();
        log.error(
                "Kafka DLT record received: topic={}, partition={}, offset={}, key={}, "
                        + "payloadBytes={}, originalTopic={}, originalPartition={}, "
                        + "originalOffset={}, exceptionClass={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                record.value() == null ? 0 : record.value().length,
                textHeader(record, KafkaHeaders.DLT_ORIGINAL_TOPIC),
                intHeader(record, KafkaHeaders.DLT_ORIGINAL_PARTITION),
                longHeader(record, KafkaHeaders.DLT_ORIGINAL_OFFSET),
                textHeader(record, KafkaHeaders.DLT_EXCEPTION_FQCN)
        );
    }

    private String textHeader(ConsumerRecord<?, ?> record, String name) {
        byte[] value = headerValue(record, name);
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }

    private Integer intHeader(ConsumerRecord<?, ?> record, String name) {
        byte[] value = headerValue(record, name);
        return value == null || value.length != Integer.BYTES
                ? null
                : ByteBuffer.wrap(value).getInt();
    }

    private Long longHeader(ConsumerRecord<?, ?> record, String name) {
        byte[] value = headerValue(record, name);
        return value == null || value.length != Long.BYTES
                ? null
                : ByteBuffer.wrap(value).getLong();
    }

    private byte[] headerValue(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : header.value();
    }
}
