package com.ibm.consulting.sim.meeting.api;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.meeting.application.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/v1")
public class MeetingController {

    private static final Logger log = LoggerFactory.getLogger(MeetingController.class);
    private static final long SSE_TIMEOUT_MS = 60_000L;

    private final MeetingPreparationService preparationService;
    private final MeetingService meetingService;
    private final GuidedMeetingResponseService guidedResponseService;
    private final ExecutorService sseExecutor;

    public MeetingController(MeetingPreparationService preparationService, MeetingService meetingService,
                             GuidedMeetingResponseService guidedResponseService,
                             @Qualifier("aiGatewayExecutor") ExecutorService sseExecutor) {
        this.preparationService = preparationService;
        this.meetingService = meetingService;
        this.guidedResponseService = guidedResponseService;
        this.sseExecutor = sseExecutor;
    }

    record PreparationRequest(
            @Size(max = MeetingRequestLimits.OBJECTIVE_MAX_LENGTH) String objective,
            @Size(max = MeetingRequestLimits.PLAN_MAX_ITEMS)
            List<@NotBlank @Size(max = MeetingRequestLimits.PLAN_ITEM_MAX_LENGTH) String> agenda,
            @Size(max = MeetingRequestLimits.PLAN_MAX_ITEMS)
            List<@NotBlank @Size(max = MeetingRequestLimits.PLAN_ITEM_MAX_LENGTH) String> discoveryQuestions) {}
    record MessageRequest(
            @NotBlank @Size(max = MeetingRequestLimits.MESSAGE_MAX_LENGTH) String message,
            @Size(max = MeetingRequestLimits.MESSAGE_ID_MAX_LENGTH) String messageId) {}

    @PutMapping("/engagements/{engagementId}/preparation")
    MeetingPreparationResponse updatePreparation(@PathVariable UUID engagementId,
                                                  @Valid @RequestBody PreparationRequest req,
                                                  @AuthenticationPrincipal User user) {
        return preparationService.update(engagementId, user.getId(), req.objective(),
                req.agenda() != null ? req.agenda() : List.of(),
                req.discoveryQuestions() != null ? req.discoveryQuestions() : List.of());
    }

    @GetMapping("/engagements/{engagementId}/preparation")
    MeetingPreparationResponse getPreparation(@PathVariable UUID engagementId, @AuthenticationPrincipal User user) {
        return preparationService.get(engagementId, user.getId());
    }

    @PostMapping("/engagements/{engagementId}/meetings")
    @ResponseStatus(HttpStatus.CREATED)
    MeetingResponse startMeeting(@PathVariable UUID engagementId, @AuthenticationPrincipal User user) {
        return meetingService.start(engagementId, user.getId());
    }

    @GetMapping("/meetings/{meetingId}")
    MeetingResponse getMeeting(@PathVariable UUID meetingId, @AuthenticationPrincipal User user) {
        return meetingService.get(meetingId, user.getId());
    }

    @GetMapping("/meetings/{meetingId}/transcript")
    List<ConversationTurnResponse> transcript(@PathVariable UUID meetingId, @AuthenticationPrincipal User user) {
        return meetingService.transcript(meetingId, user.getId());
    }

    @GetMapping("/meetings/{meetingId}/persona-state")
    PersonaStateResponse personaState(@PathVariable UUID meetingId, @AuthenticationPrincipal User user) {
        return meetingService.personaState(meetingId, user.getId());
    }

    @GetMapping("/meetings/{meetingId}/response-options")
    MeetingResponseOptionsResponse responseOptions(@PathVariable UUID meetingId, @AuthenticationPrincipal User user) {
        return guidedResponseService.optionsFor(meetingId, user.getId());
    }

    /**
     * Streams the persona's reply as Server-Sent Events (§2, §6.2). The learner message
     * is validated, persisted and evaluated synchronously on a worker thread; the
     * resulting text is then streamed to the client in chunks so the UI can render it
     * progressively, followed by a final event carrying the validated structured payload.
     */
    @PostMapping(path = "/meetings/{meetingId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter sendMessage(@PathVariable UUID meetingId,
                           @Valid @RequestBody MessageRequest req,
                           @AuthenticationPrincipal User user) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        UUID userId = user.getId();
        AtomicBoolean cancellationRequested = new AtomicBoolean();
        AtomicReference<Future<?>> taskReference = new AtomicReference<>();
        Runnable cancelTask = () -> {
            cancellationRequested.set(true);
            Future<?> task = taskReference.get();
            if (task != null) {
                task.cancel(true);
            }
        };
        emitter.onTimeout(cancelTask);
        emitter.onCompletion(cancelTask);
        emitter.onError(ignored -> cancelTask.run());

        Future<?> task = sseExecutor.submit(() -> {
            try {
                ensureNotInterrupted();
                emitter.send(SseEmitter.event().name("turn.thinking").data(Map.of("status", "processing")));

                MeetingTurnResult result = meetingService.sendMessage(meetingId, userId, req.message(), req.messageId());
                ensureNotInterrupted();

                // Chunk multiple words per SSE frame (instead of one word at a time) so the
                // simulated "typing" effect adds only a small, bounded amount of latency
                // regardless of reply length — a long reply no longer costs 20ms per word
                // (P0 perf fix: this was a self-inflicted delay on top of DB/network latency).
                String[] words = result.personaTurn().content().split(" ");
                StringBuilder accumulated = new StringBuilder();
                int chunkSize = 3;
                for (int i = 0; i < words.length; i++) {
                    ensureNotInterrupted();
                    accumulated.append(words[i]).append(' ');
                    boolean lastWord = i == words.length - 1;
                    if (lastWord || (i + 1) % chunkSize == 0) {
                        emitter.send(SseEmitter.event().name("turn.delta")
                                .data(Map.of("text", accumulated.toString().trim())));
                        if (!lastWord) {
                            Thread.sleep(15);
                        }
                    }
                }

                emitter.send(SseEmitter.event().name("turn.complete").data(result));
                emitter.complete();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                log.debug("SSE meeting turn cancelled for meeting {}", meetingId);
            } catch (Exception e) {
                log.error("SSE meeting turn failed for meeting {}", meetingId, e);
                emitter.completeWithError(e);
            }
        });
        taskReference.set(task);
        if (cancellationRequested.get()) {
            task.cancel(true);
        }

        return emitter;
    }

    private void ensureNotInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("SSE meeting task was cancelled");
        }
    }

    @PostMapping("/meetings/{meetingId}/complete")
    MeetingResponse complete(@PathVariable UUID meetingId, @AuthenticationPrincipal User user) {
        return meetingService.complete(meetingId, user.getId());
    }

    @PostMapping("/meetings/{meetingId}/retry")
    MeetingResponse retry(@PathVariable UUID meetingId, @AuthenticationPrincipal User user) {
        return meetingService.retry(meetingId, user.getId());
    }
}
