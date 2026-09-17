package com.ibm.consulting.sim.engagement.api;

import com.ibm.consulting.sim.engagement.application.EngagementQueryService;
import com.ibm.consulting.sim.engagement.application.RetryEngagementUseCase;
import com.ibm.consulting.sim.engagement.application.StartEngagementUseCase;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EngagementController.class)
@AutoConfigureMockMvc(addFilters = false)
class EngagementControllerStartTest {

    @Autowired MockMvc mockMvc;
    @MockBean StartEngagementUseCase startUseCase;
    @MockBean EngagementQueryService queryService;
    @MockBean RetryEngagementUseCase retryUseCase;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    private final User learner = User.create(
            "engagement@example.com", "hash", "Engagement Learner", UserRole.LEARNER);

    @Test
    void validStartFromLeadReturnsCreatedAndForwardsTheAuthenticatedLearner() throws Exception {
        UUID leadId = UUID.randomUUID();
        UUID personaId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/engagements/from-lead")
                        .with(learnerAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leadId":"%s","personaId":"%s"}
                                """.formatted(leadId, personaId)))
                .andExpect(status().isCreated());

        verify(startUseCase).executeForLead(learner.getId(), leadId, personaId);
    }

    @Test
    void archivedScenarioIsMappedToADomainProblemForBothStartPaths() throws Exception {
        UUID scenarioId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        StartEngagementUseCase.ScenarioUnavailableException failure =
                new StartEngagementUseCase.ScenarioUnavailableException(scenarioId);
        when(startUseCase.execute(learner.getId(), scenarioId, null)).thenThrow(failure);
        when(startUseCase.executeForLead(learner.getId(), leadId, null)).thenThrow(failure);

        assertDomainFailure(post("/api/v1/engagements")
                .with(learnerAuthentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"scenarioId":"%s"}
                        """.formatted(scenarioId)), scenarioId);
        assertDomainFailure(post("/api/v1/engagements/from-lead")
                .with(learnerAuthentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"leadId":"%s"}
                        """.formatted(leadId)), scenarioId);
    }

    @Test
    void retryFromAnInvalidLifecycleStateReturnsTheEstablishedDomainProblem() throws Exception {
        UUID engagementId = UUID.randomUUID();
        when(retryUseCase.execute(engagementId, learner.getId()))
                .thenThrow(new RetryEngagementUseCase.RetryNotAvailableException(
                        "Only an engagement that failed its meeting can be restarted."));

        mockMvc.perform(post("/api/v1/engagements/{id}/retry", engagementId)
                        .with(learnerAuthentication()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("https://consulting-sim.ibm.com/problems/domain-error"))
                .andExpect(jsonPath("$.detail").value(
                        "Only an engagement that failed its meeting can be restarted."));
    }

    private void assertDomainFailure(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            UUID scenarioId) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("https://consulting-sim.ibm.com/problems/domain-error"))
                .andExpect(jsonPath("$.detail").value(
                        "Scenario " + scenarioId + " is not published for new engagements"));
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
