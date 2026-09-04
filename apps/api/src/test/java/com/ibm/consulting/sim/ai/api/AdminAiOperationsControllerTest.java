package com.ibm.consulting.sim.ai.api;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ibm.consulting.sim.ai.application.AiOperationsResponse;
import com.ibm.consulting.sim.ai.application.AiOperationsService;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.identity.infrastructure.SecurityConfig;

@WebMvcTest(AdminAiOperationsController.class)
@Import(SecurityConfig.class)
class AdminAiOperationsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiOperationsService operationsService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    // tests unauthorised access to the ai operations endpoint
    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ai/operations"))
                .andExpect(status().isUnauthorized());
    }

    // tests forbidden backend response for roles without ai operations access
    @Test
    @WithMockUser(roles = "LEARNER")
    void learnerCannotViewAiOperations() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ai/operations"))
                .andExpect(status().isForbidden());
    }

    // tests forbidden backend response for roles without ai operations access
    @Test
    @WithMockUser(roles = "SCENARIO_AUTHOR")
    void scenarioAuthorCannotViewAiOperations() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ai/operations"))
                .andExpect(status().isForbidden());
    }

    // tests successful backend response for reviewer role
    @Test
    @WithMockUser(roles = "REVIEWER")
    void reviewerCanViewAiOperations() throws Exception {
        when(operationsService.snapshot()).thenReturn(new AiOperationsResponse(false, List.of(), Map.of(), false, 1));

        mockMvc.perform(get("/api/v1/admin/ai/operations"))
                .andExpect(status().isOk());
    }

    // tests successful backend response for administrator role
    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void administratorCanViewAiOperations() throws Exception {
        when(operationsService.snapshot()).thenReturn(new AiOperationsResponse(false, List.of(), Map.of(), false, 1));

        mockMvc.perform(get("/api/v1/admin/ai/operations"))
                .andExpect(status().isOk());
    }
}
