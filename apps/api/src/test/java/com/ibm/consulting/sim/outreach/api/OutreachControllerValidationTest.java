package com.ibm.consulting.sim.outreach.api;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.outreach.application.CapabilityBriefService;
import com.ibm.consulting.sim.outreach.application.OutreachService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(OutreachController.class)
@AutoConfigureMockMvc(addFilters = false)
class OutreachControllerValidationTest {

    @Autowired MockMvc mockMvc;
    @MockBean OutreachService outreachService;
    @MockBean CapabilityBriefService capabilityBriefService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    private final UUID engagementId = UUID.randomUUID();
    private final User learner = User.create(
            "outreach@example.com", "hash", "Outreach Learner", UserRole.LEARNER);

    @Test
    void requestIdIsForwardedToTheIdempotentServiceContract() throws Exception {
        send("request-123").andExpect(status().isCreated());

        verify(outreachService).send(
                engagementId, learner.getId(), "Valid subject", "Valid outreach body", "request-123");
    }

    @Test
    void omittedRequestIdRemainsBackwardCompatible() throws Exception {
        mockMvc.perform(post("/api/v1/engagements/{id}/outreach", engagementId)
                        .with(learnerAuthentication())
                        .contentType("application/json")
                        .content("""
                                {"subject":"Valid subject","body":"Valid outreach body"}
                                """))
                .andExpect(status().isCreated());

        verify(outreachService).send(
                engagementId, learner.getId(), "Valid subject", "Valid outreach body", null);
    }

    @Test
    void invalidLifecycleStateReturnsTheEstablishedDomainProblem() throws Exception {
        when(outreachService.send(engagementId, learner.getId(),
                "Valid subject", "Valid outreach body", null))
                .thenThrow(new OutreachService.InvalidOutreachStateException(EngagementState.QUALIFYING));

        sendJson("{\"subject\":\"Valid subject\",\"body\":\"Valid outreach body\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("https://consulting-sim.ibm.com/problems/domain-error"))
                .andExpect(jsonPath("$.detail").value("Cannot send outreach in state: QUALIFYING"));
    }

    @Test
    void oversizedRequestIdIsRejectedBeforeApplicationLogic() throws Exception {
        send("r".repeat(101)).andExpect(status().isBadRequest());

        verifyNoInteractions(outreachService);
    }

    @Test
    void invalidOutreachFieldsAreRejectedBeforeApplicationLogic() throws Exception {
        sendJson("{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.subject").exists())
                .andExpect(jsonPath("$.violations.body").exists());
        sendJson("{\"subject\":null,\"body\":null}").andExpect(status().isBadRequest());
        sendJson("{\"subject\":\"   \",\"body\":\"   \"}").andExpect(status().isBadRequest());
        sendJson("{\"subject\":\"%s\",\"body\":\"valid\"}".formatted("s".repeat(201)))
                .andExpect(status().isBadRequest());
        sendJson("{\"subject\":\"valid\",\"body\":\"%s\"}".formatted("b".repeat(5001)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(outreachService);
    }

    @Test
    void maximumOutreachFieldsRemainAccepted() throws Exception {
        String subject = "s".repeat(200);
        String body = "b".repeat(5000);

        sendJson("{\"subject\":\"%s\",\"body\":\"%s\"}".formatted(subject, body))
                .andExpect(status().isCreated());

        verify(outreachService).send(engagementId, learner.getId(), subject, body, null);
    }

    @Test
    void invalidCapabilityBriefFieldsAreRejectedBeforeApplicationLogic() throws Exception {
        String endpoint = "/api/v1/engagements/{id}/outreach/capability-brief";
        mockMvc.perform(post(endpoint, engagementId).with(learnerAuthentication())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.violations.relevantExperience").exists())
                .andExpect(jsonPath("$.violations.approach").exists())
                .andExpect(jsonPath("$.violations.caseExample").exists())
                .andExpect(jsonPath("$.violations.clientFit").exists());
        mockMvc.perform(post(endpoint, engagementId).with(learnerAuthentication())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"relevantExperience":"valid","approach":"   ",
                                 "caseExample":"valid","clientFit":"valid"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(endpoint, engagementId).with(learnerAuthentication())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"relevantExperience":"%s","approach":"valid",
                                 "caseExample":"valid","clientFit":"valid"}
                                """.formatted("x".repeat(3001))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(capabilityBriefService);
    }

    @Test
    void malformedBodyWrongScalarAndInvalidPathReturnSafeBadRequests() throws Exception {
        sendJson("{\"subject\":\"valid\",\"body\":")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type")
                        .value("https://consulting-sim.ibm.com/problems/malformed-request"));
        sendJson("{\"subject\":[\"wrong\"],\"body\":\"valid\"}")
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/engagements/not-a-uuid/outreach")
                        .with(learnerAuthentication()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"valid\",\"body\":\"valid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail")
                        .value("A request parameter or path value has an invalid format."));

        verifyNoInteractions(outreachService);
    }

    private org.springframework.test.web.servlet.ResultActions send(String requestId) throws Exception {
        return mockMvc.perform(post("/api/v1/engagements/{id}/outreach", engagementId)
                .with(learnerAuthentication())
                .contentType("application/json")
                .content("""
                        {"subject":"Valid subject","body":"Valid outreach body","requestId":"%s"}
                        """.formatted(requestId)));
    }

    private org.springframework.test.web.servlet.ResultActions sendJson(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/engagements/{id}/outreach", engagementId)
                .with(learnerAuthentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
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
