package com.ibm.consulting.sim.meeting.api;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.meeting.application.GuidedMeetingResponseService;
import com.ibm.consulting.sim.meeting.application.MeetingPreparationService;
import com.ibm.consulting.sim.meeting.application.MeetingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeetingController.class)
@AutoConfigureMockMvc(addFilters = false)
class MeetingHttpContractTest {

    @Autowired MockMvc mockMvc;
    @MockBean MeetingPreparationService preparationService;
    @MockBean MeetingService meetingService;
    @MockBean GuidedMeetingResponseService guidedResponseService;
    @MockBean(name = "aiGatewayExecutor") ExecutorService executor;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    private final UUID meetingId = UUID.randomUUID();
    private final User learner = User.create("http@example.com", "hash", "HTTP Learner", UserRole.LEARNER);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(executor.submit(any(Runnable.class))).thenReturn(mock(Future.class));
    }

    @Test
    void unsupportedMethodsReturnSafeMethodNotAllowedProblems() throws Exception {
        mockMvc.perform(get("/api/v1/meetings/{id}/messages", meetingId).with(authentication()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(post("/api/v1/meetings/{id}/transcript", meetingId).with(authentication()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verify(executor, never()).submit(any(Runnable.class));
    }

    @Test
    void unsupportedOrMissingRequestContentTypeIsRejectedBeforeExecution() throws Exception {
        String body = "{\"message\":\"Hello\",\"messageId\":\"message-1\"}";
        mockMvc.perform(post("/api/v1/meetings/{id}/messages", meetingId).with(authentication())
                        .contentType(MediaType.TEXT_PLAIN).content(body))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(post("/api/v1/meetings/{id}/messages", meetingId).with(authentication()).content(body))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verify(executor, never()).submit(any(Runnable.class));
    }

    @Test
    void unacceptableResponseTypeIsRejectedBeforeExecution() throws Exception {
        mockMvc.perform(post("/api/v1/meetings/{id}/messages", meetingId).with(authentication())
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_XML)
                        .content("{\"message\":\"Hello\",\"messageId\":\"message-1\"}"))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verify(executor, never()).submit(any(Runnable.class));
    }

    @Test
    void validJsonRequestRemainsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/meetings/{id}/messages", meetingId).with(authentication())
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"Hello\",\"messageId\":\"message-1\"}"))
                .andExpect(status().isOk());

        verify(executor).submit(any(Runnable.class));
    }

    private RequestPostProcessor authentication() {
        return request -> {
            Authentication authentication =
                    new UsernamePasswordAuthenticationToken(learner, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setUserPrincipal(authentication);
            return request;
        };
    }
}
