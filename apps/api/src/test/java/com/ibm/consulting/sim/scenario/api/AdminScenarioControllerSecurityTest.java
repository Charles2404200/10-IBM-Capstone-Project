package com.ibm.consulting.sim.scenario.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.identity.infrastructure.SecurityConfig;
import com.ibm.consulting.sim.scenario.application.CreateScenarioRequest;
import com.ibm.consulting.sim.scenario.application.ScenarioService;
import com.ibm.consulting.sim.scenario.application.ScenarioSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that scenario/persona authoring is restricted to
 * {@code SCENARIO_AUTHOR} and {@code ADMINISTRATOR}; a plain learner must
 * never be able to create or mutate simulation content.
 */
@WebMvcTest(AdminScenarioController.class)
@Import(SecurityConfig.class)
class AdminScenarioControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ScenarioService scenarioService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    private final CreateScenarioRequest request =
            new CreateScenarioRequest("Title", "Retail", "Description", 3);

    @Test
    @WithMockUser(roles = "LEARNER")
    void learnerCannotCreateScenario() throws Exception {
        mockMvc.perform(post("/api/v1/admin/scenarios")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SCENARIO_AUTHOR")
    void scenarioAuthorCanCreateScenario() throws Exception {
        when(scenarioService.create(org.mockito.ArgumentMatchers.any())).thenReturn(
                new ScenarioSummary(UUID.randomUUID(), "Title", "Retail", "Description", 3, 1, "DRAFT", List.of(), java.util.Map.of(),
                        new ScenarioSummary.DifficultyProfile(3, 3, 3),
                        new ScenarioSummary.Briefing("Management Consultant", "", List.of(), 10)));

        mockMvc.perform(post("/api/v1/admin/scenarios")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
