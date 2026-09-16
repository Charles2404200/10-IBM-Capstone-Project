package com.ibm.consulting.sim.lead.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.lead.application.LeadNotSelectedException;
import com.ibm.consulting.sim.lead.application.LeadService;
import com.ibm.consulting.sim.lead.application.ResearchIntelligenceService;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeadController.class)
@AutoConfigureMockMvc(addFilters = false)
class LeadControllerValidationTest {

    private static final String VALID_NOTE = "Evidence-backed observation";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean LeadService leadService;
    @MockBean ResearchIntelligenceService researchIntelligenceService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    private final UUID engagementId = UUID.randomUUID();
    private final User learner = User.create(
            "researcher@example.com", "hash", "Researcher", UserRole.LEARNER);

    @Test
    void blankNoteIsRejectedWithAValidationProblem() throws Exception {
        assertViolation(validPayload(Map.of("note", "   ")), "note");
    }

    @Test
    void missingEvidenceTypeIsRejectedWithAValidationProblem() throws Exception {
        assertViolation(Map.of("note", VALID_NOTE, "relevanceScore", 50), "evidenceType");
    }

    @Test
    void relevanceScoreBelowZeroIsRejected() throws Exception {
        assertViolation(validPayload(Map.of("relevanceScore", -1)), "relevanceScore");
    }

    @Test
    void relevanceScoreAboveOneHundredIsRejected() throws Exception {
        assertViolation(validPayload(Map.of("relevanceScore", 101)), "relevanceScore");
    }

    @Test
    void oversizedSourceMetadataIsRejected() throws Exception {
        assertViolation(validPayload(Map.of("sourceUrl", "u".repeat(501))), "sourceUrl");
        assertViolation(validPayload(Map.of("sourceTitle", "t".repeat(301))), "sourceTitle");
    }

    @Test
    void malformedJsonIsRejectedBeforeApplicationLogic() throws Exception {
        mockMvc.perform(researchRequest("{\"note\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("https://consulting-sim.ibm.com/problems/malformed-request"));

        verifyNoInteractions(leadService);
    }

    @Test
    void validResearchReachesApplicationLogic() throws Exception {
        mockMvc.perform(researchRequest(objectMapper.writeValueAsString(validPayload(Map.of()))))
                .andExpect(status().isCreated());

        verify(leadService).saveEvidence(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void savingResearchWithoutASelectedLeadReturnsMappedDomainProblem() throws Exception {
        when(leadService.saveEvidence(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new LeadNotSelectedException(engagementId));

        mockMvc.perform(researchRequest(objectMapper.writeValueAsString(validPayload(Map.of()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("https://consulting-sim.ibm.com/problems/domain-error"))
                .andExpect(jsonPath("$.detail").value(
                        "No lead has been selected for engagement " + engagementId));
    }

    @Test
    void readingIntelligenceWithoutASelectedLeadReturnsMappedDomainProblem() throws Exception {
        when(leadService.getIntelligence(engagementId, learner.getId()))
                .thenThrow(new LeadNotSelectedException(engagementId));

        mockMvc.perform(get("/api/v1/engagements/{id}/lead-intelligence", engagementId)
                        .with(learnerAuthentication()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("https://consulting-sim.ibm.com/problems/domain-error"))
                .andExpect(jsonPath("$.detail").value(
                        "No lead has been selected for engagement " + engagementId));
    }

    private void assertViolation(Map<String, Object> payload, String field) throws Exception {
        mockMvc.perform(researchRequest(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("https://consulting-sim.ibm.com/problems/validation-error"))
                .andExpect(jsonPath("$.violations." + field).exists());
        verifyNoInteractions(leadService);
    }

    private Map<String, Object> validPayload(Map<String, Object> overrides) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("note", VALID_NOTE);
        payload.put("evidenceType", "COMPANY_NEWS");
        payload.put("relevanceScore", 50);
        payload.putAll(overrides);
        return payload;
    }

    private RequestBuilder researchRequest(String json) {
        return post("/api/v1/engagements/{id}/research", engagementId)
                .with(learnerAuthentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
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
