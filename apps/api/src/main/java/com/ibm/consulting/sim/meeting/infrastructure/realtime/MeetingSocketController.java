package com.ibm.consulting.sim.meeting.infrastructure.realtime;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.meeting.application.MeetingService;
import com.ibm.consulting.sim.meeting.application.MeetingTurnResult;
import com.ibm.consulting.sim.meeting.application.GuidedMeetingResponseService;
import com.ibm.consulting.sim.meeting.application.MeetingResponseOptionsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * WebSocket/STOMP counterpart of {@code MeetingController#sendMessage} (the SSE
 * endpoint, kept intact as a fallback transport / for any non-browser client).
 * Publishes the same {@code turn.thinking} / {@code turn.delta} / {@code turn.complete}
 * event vocabulary the frontend's SSE hook already understood, so
 * {@code useMeetingSocket} can expose an identical external shape to the page —
 * only the transport underneath changes, from one HTTP request per message to a
 * single persistent connection per meeting session.
 *
 * <p>Publishes to the per-meeting topic {@code /topic/meetings/{meetingId}}, which
 * {@link MeetingSubscriptionInterceptor} guards so only the owning learner can
 * subscribe.
 */
@Controller
public class MeetingSocketController {

    private static final Logger log = LoggerFactory.getLogger(MeetingSocketController.class);

    private final MeetingService meetingService;
    private final GuidedMeetingResponseService guidedResponseService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ExecutorService executor;

    public MeetingSocketController(MeetingService meetingService,
                                   GuidedMeetingResponseService guidedResponseService,
                                   SimpMessagingTemplate messagingTemplate,
                                   @Qualifier("aiGatewayExecutor") ExecutorService executor) {
        this.meetingService = meetingService;
        this.guidedResponseService = guidedResponseService;
        this.messagingTemplate = messagingTemplate;
        this.executor = executor;
    }

    record MeetingMessage(String message, String messageId) {}
    record SocketEvent(String type, Object payload) {}

    @MessageMapping("/meetings/{meetingId}/send")
    public void sendMessage(@DestinationVariable UUID meetingId, MeetingMessage payload, Principal principal) {
        UUID userId = resolveUserId(principal);
        String topic = "/topic/meetings/" + meetingId;

        executor.execute(() -> {
            try {
                messagingTemplate.convertAndSend(topic,
                        new SocketEvent("turn.thinking", Map.of("status", "processing")));

                MeetingTurnResult result = meetingService.sendMessage(
                        meetingId, userId, payload.message(), payload.messageId());

                // Transport the real provider output as incremental socket events. Persistence
                // happens before this point; the browser renders these events from local state
                // and never needs to reload the transcript to reveal a response.
                String[] words = result.personaTurn().content().split(" ");
                StringBuilder accumulated = new StringBuilder();
                for (int index = 0; index < words.length; index++) {
                    accumulated.append(words[index]).append(' ');
                    boolean isLastWord = index == words.length - 1;
                    if (isLastWord || (index + 1) % 4 == 0) {
                        messagingTemplate.convertAndSend(topic,
                                new SocketEvent("turn.delta", Map.of("text", accumulated.toString().trim())));
                        if (!isLastWord) Thread.sleep(12);
                    }
                }
                messagingTemplate.convertAndSend(topic, new SocketEvent("turn.complete", result));
                generateFallbackOptionsWhenNeeded(result, meetingId, userId, topic);
            } catch (Exception e) {
                log.error("WebSocket meeting turn failed for meeting {}", meetingId, e);
                messagingTemplate.convertAndSend(topic,
                        new SocketEvent("turn.error", Map.of("message", "Failed to process message")));
            }
        });
    }

    /**
     * A provider may return a valid persona response but omit the optional guided
     * choices. Keep the live reply fast, then recover choices asynchronously on
     * the same socket instead of asking the learner to retry a broken turn.
     */
    private void generateFallbackOptionsWhenNeeded(MeetingTurnResult result, UUID meetingId, UUID userId, String topic) {
        if (result.responseOptions() == null || result.responseOptions().available()
                || result.responseOptions().interactionMode() != com.ibm.consulting.sim.meeting.domain.MeetingInteractionMode.GUIDED) {
            return;
        }
        executor.execute(() -> {
            try {
                MeetingResponseOptionsResponse options = guidedResponseService.optionsFor(meetingId, userId);
                if (options.available()) {
                    messagingTemplate.convertAndSend(topic, new SocketEvent("turn.options", options));
                } else {
                    messagingTemplate.convertAndSend(topic, new SocketEvent("turn.options.error",
                            Map.of("message", options.unavailableReason() == null
                                    ? "Guided responses could not be prepared. Please try again."
                                    : options.unavailableReason())));
                }
            } catch (Exception exception) {
                log.error("Guided response fallback failed for meeting {}", meetingId, exception);
                messagingTemplate.convertAndSend(topic, new SocketEvent("turn.options.error",
                        Map.of("message", "Guided responses could not be prepared. Please try again.")));
            }
        });
    }

    private UUID resolveUserId(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof User user) {
            return user.getId();
        }
        throw new IllegalStateException("WebSocket session has no authenticated user");
    }
}
