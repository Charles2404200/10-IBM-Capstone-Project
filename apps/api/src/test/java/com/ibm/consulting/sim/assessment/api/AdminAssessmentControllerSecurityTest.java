package com.ibm.consulting.sim.assessment.api;

import com.ibm.consulting.sim.assessment.application.AssessmentService;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.identity.infrastructure.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the coaching/review view of a learner's assessment is
 * restricted to {@code REVIEWER}/{@code ADMINISTRATOR} and is not reachable
 * by a plain learner, since it bypasses the engagement-ownership check by
 * design.
 */
@WebMvcTest(AdminAssessmentController.class)
@Import(SecurityConfig.class)
class AdminAssessmentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AssessmentService assessmentService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "LEARNER")
    void learnerCannotViewOtherAssessment() throws Exception {
        mockMvc.perform(get("/api/v1/admin/engagements/{id}/assessment", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "REVIEWER")
    void reviewerCanViewAssessment() throws Exception {
        org.mockito.Mockito.when(assessmentService.getForReview(org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);

        mockMvc.perform(get("/api/v1/admin/engagements/{id}/assessment", UUID.randomUUID()))
                .andExpect(status().isOk());
    }
}
