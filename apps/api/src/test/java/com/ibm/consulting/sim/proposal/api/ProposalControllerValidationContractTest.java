package com.ibm.consulting.sim.proposal.api;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.proposal.application.ProposalService;
import com.ibm.consulting.sim.proposal.domain.ProposalDraftContent;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

@WebMvcTest(ProposalController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProposalControllerValidationContractTest {

    @Autowired MockMvc mockMvc;
    @MockBean ProposalService proposalService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    private final UUID engagementId = UUID.randomUUID();
    private final User learner = User.create("learner@example.com", "hash", "Learner", UserRole.LEARNER);

    @Test
    void malformedDraftReviewAndChallengePayloadsReturnValidationProblems() throws Exception {
        mockMvc.perform(put(path("draft")).with(authentication()).contentType("application/json")
                        .content("{\"budget\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.budget").exists());
        mockMvc.perform(post(path("review")).with(authentication()).contentType("application/json")
                        .content("{\"timelineWeeks\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.timelineWeeks").exists());
        mockMvc.perform(post(path("challenge")).with(authentication()).contentType("application/json")
                        .content("{\"risks\":[{\"risk\":\"Risk\",\"severity\":\"CRITICAL\",\"mitigation\":\"Plan\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations['risks[0].severity']").exists());

        verifyNoInteractions(proposalService);
    }

    @Test
    void budgetMustBeAJsonNumberInsteadOfAString() throws Exception {
        mockMvc.perform(put(path("draft")).with(authentication()).contentType("application/json")
                        .content("{\"budget\":\"1000.00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type")
                        .value("https://consulting-sim.ibm.com/problems/malformed-request"));

        verifyNoInteractions(proposalService);
    }

    @Test
    void legitimatelyIncompleteDraftRemainsSupported() throws Exception {
        mockMvc.perform(put(path("draft")).with(authentication()).contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(ProposalDraftContent.class);
        verify(proposalService).saveDraft(eq(engagementId), eq(learner.getId()), captor.capture());
        assertThat(captor.getValue().problemStatement()).isEmpty();
        assertThat(captor.getValue().budget()).isZero();
        assertThat(captor.getValue().timelineWeeks()).isEqualTo(1);
    }

    @Test
    void invalidProposalStateReturnsTheEstablishedDomainProblem() throws Exception {
        when(proposalService.review(eq(engagementId), eq(learner.getId()), any()))
                .thenThrow(new ProposalService.InvalidProposalStateException(EngagementState.QUALIFYING));

        mockMvc.perform(post(path("review")).with(authentication()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("https://consulting-sim.ibm.com/problems/domain-error"))
                .andExpect(jsonPath("$.detail").value("Cannot edit a proposal in state: QUALIFYING"));
    }

    @Test
    void submissionRequiresItsEstablishedMandatoryFieldsBeforeServiceInvocation() throws Exception {
        mockMvc.perform(post("/api/v1/engagements/{id}/proposal", engagementId)
                        .with(authentication()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.problemStatement").exists())
                .andExpect(jsonPath("$.violations.components").exists())
                .andExpect(jsonPath("$.violations.budget").exists())
                .andExpect(jsonPath("$.violations.timelineWeeks").exists());

        verifyNoInteractions(proposalService);
    }

    private String path(String action) {
        return "/api/v1/engagements/" + engagementId + "/proposal/" + action;
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
