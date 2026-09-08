package com.ibm.consulting.sim.meeting.api;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.meeting.application.GuidedMeetingResponseService;
import com.ibm.consulting.sim.meeting.application.MeetingPreparationResponse;
import com.ibm.consulting.sim.meeting.application.MeetingPreparationService;
import com.ibm.consulting.sim.meeting.application.MeetingRequestLimits;
import com.ibm.consulting.sim.meeting.application.MeetingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeetingController.class)
@AutoConfigureMockMvc(addFilters = false)
class MeetingControllerValidationTest {

    @Autowired MockMvc mockMvc;
    @MockBean MeetingPreparationService preparationService;
    @MockBean MeetingService meetingService;
    @MockBean GuidedMeetingResponseService guidedResponseService;
    @MockBean(name = "aiGatewayExecutor") ExecutorService executor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    private final UUID engagementId = UUID.randomUUID();
    private final UUID meetingId = UUID.randomUUID();
    private final User learner = User.create(
            "learner@example.com", "hash", "Learner", UserRole.LEARNER);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(executor.submit(any(Runnable.class))).thenReturn(mock(Future.class));
    }

    @Test
    void normalAndMaximumSizedMessagesAreAccepted() throws Exception {
        sendMessage("A concise discovery question", "message-1").andExpect(status().isOk());
        sendMessage("m".repeat(MeetingRequestLimits.MESSAGE_MAX_LENGTH),
                "i".repeat(MeetingRequestLimits.MESSAGE_ID_MAX_LENGTH)).andExpect(status().isOk());

        verify(executor, org.mockito.Mockito.times(2)).submit(any(Runnable.class));
    }

    @Test
    void oversizedMessageAndMessageIdAreRejectedBeforeMeetingExecution() throws Exception {
        sendMessage("m".repeat(MeetingRequestLimits.MESSAGE_MAX_LENGTH + 1), "message-1")
                .andExpect(status().isBadRequest());
        sendMessage("valid", "i".repeat(MeetingRequestLimits.MESSAGE_ID_MAX_LENGTH + 1))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(meetingService);
        verify(executor, never()).submit(any(Runnable.class));
    }

    @Test
    void maximumPreparationPayloadIsAccepted() throws Exception {
        String objective = "o".repeat(MeetingRequestLimits.OBJECTIVE_MAX_LENGTH);
        List<String> items = java.util.stream.IntStream.range(0, MeetingRequestLimits.PLAN_MAX_ITEMS)
                .mapToObj(ignored -> "p".repeat(MeetingRequestLimits.PLAN_ITEM_MAX_LENGTH)).toList();
        when(preparationService.update(any(), any(), anyString(), any(), any()))
                .thenReturn(new MeetingPreparationResponse(
                        UUID.randomUUID(), engagementId, objective, items, items, 100, true));

        updatePreparation(objective, items, items).andExpect(status().isOk());

        verify(preparationService).update(engagementId, learner.getId(), objective, items, items);
    }

    @Test
    void everyOversizedPreparationDimensionIsRejectedBeforeTheService() throws Exception {
        List<String> normal = List.of("Discuss impact");
        List<String> tooMany = java.util.stream.IntStream.range(0, MeetingRequestLimits.PLAN_MAX_ITEMS + 1)
                .mapToObj(index -> "Item " + index).toList();
        List<String> oversizedItem = List.of("x".repeat(MeetingRequestLimits.PLAN_ITEM_MAX_LENGTH + 1));

        updatePreparation("o".repeat(MeetingRequestLimits.OBJECTIVE_MAX_LENGTH + 1), normal, normal)
                .andExpect(status().isBadRequest());
        updatePreparation("Objective", tooMany, normal).andExpect(status().isBadRequest());
        updatePreparation("Objective", oversizedItem, normal).andExpect(status().isBadRequest());
        updatePreparation("Objective", normal, tooMany).andExpect(status().isBadRequest());
        updatePreparation("Objective", normal, oversizedItem).andExpect(status().isBadRequest());

        verifyNoInteractions(preparationService);
    }

    private org.springframework.test.web.servlet.ResultActions sendMessage(String message, String messageId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/meetings/{meetingId}/messages", meetingId)
                .with(learnerAuthentication())
                .contentType("application/json")
                .content("{\"message\":\"%s\",\"messageId\":\"%s\"}".formatted(message, messageId)));
    }

    private org.springframework.test.web.servlet.ResultActions updatePreparation(
            String objective, List<String> agenda, List<String> questions) throws Exception {
        return mockMvc.perform(put("/api/v1/engagements/{engagementId}/preparation", engagementId)
                .with(learnerAuthentication())
                .contentType("application/json")
                .content("""
                        {"objective":"%s","agenda":%s,"discoveryQuestions":%s}
                        """.formatted(objective, jsonArray(agenda), jsonArray(questions))));
    }

    private String jsonArray(List<String> values) {
        return values.stream().map(value -> "\"" + value + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private RequestPostProcessor learnerAuthentication() {
        return request -> {
            Authentication authentication =
                    new UsernamePasswordAuthenticationToken(learner, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setUserPrincipal(authentication);
            return request;
        };
    }
}
