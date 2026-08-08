package com.ibm.consulting.sim.meeting.infrastructure.realtime;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.meeting.application.MeetingService;
import com.ibm.consulting.sim.meeting.application.MeetingTurnResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.Executors;

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
    private final SimpMessagingTemplate messagingTemplate;
    // AI calls take low-single-digit seconds even after the latency fixes; running
    // them on the shared STOMP inbound thread would stall delivery of every other
    // client's frames, so each send is dispatched to its own worker thread, exactly
    // as the SSE endpoint already does with its own executor.
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public MeetingSocketController(MeetingService meetingService, SimpMessagingTemplate messagingTemplate) {
        this.meetingService = meetingService;
        this.messagingTemplate = messagingTemplate;
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

                // Real AI latency is now low enough (see GeminiProvider thinkingBudget fix)
                // that the artificial per-chunk "typing" delay used by the SSE path is no
                // longer needed to make the reply feel natural; publish the full text once
                // the AI response is ready and let the client render it immediately.
                messagingTemplate.convertAndSend(topic,
                        new SocketEvent("turn.delta", Map.of("text", result.personaTurn().content())));
                messagingTemplate.convertAndSend(topic, new SocketEvent("turn.complete", result));
            } catch (Exception e) {
                log.error("WebSocket meeting turn failed for meeting {}", meetingId, e);
                messagingTemplate.convertAndSend(topic,
                        new SocketEvent("turn.error", Map.of("message", "Failed to process message")));
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
