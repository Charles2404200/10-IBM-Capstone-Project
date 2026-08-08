package com.ibm.consulting.sim.achievement.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.achievement.application.AchievementAdminView;
import com.ibm.consulting.sim.achievement.application.AdminAchievementService;
import com.ibm.consulting.sim.achievement.application.ConditionNode;
import com.ibm.consulting.sim.achievement.application.UpsertAchievementRequest;
import com.ibm.consulting.sim.achievement.domain.ConditionType;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that achievement authoring is restricted to {@code ADMINISTRATOR} —
 * neither learners nor scenario authors may create or edit gamification rules.
 */
@WebMvcTest(AdminAchievementController.class)
@Import(SecurityConfig.class)
class AdminAchievementControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminAchievementService adminAchievementService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    private final UpsertAchievementRequest request = new UpsertAchievementRequest(
            "First Win", "Win your first engagement", "trophy",
            ConditionNode.leaf(ConditionType.MIN_ENGAGEMENTS_WON, null, 1));

    @Test
    @WithMockUser(roles = "SCENARIO_AUTHOR")
    void scenarioAuthorCannotCreateAchievement() throws Exception {
        mockMvc.perform(post("/api/v1/admin/achievements")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "LEARNER")
    void learnerCannotListAchievements() throws Exception {
        mockMvc.perform(get("/api/v1/admin/achievements"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void administratorCanCreateAchievement() throws Exception {
        when(adminAchievementService.create(any())).thenReturn(
                new AchievementAdminView(UUID.randomUUID(), "First Win", "desc", "trophy", true, request.rule()));

        mockMvc.perform(post("/api/v1/admin/achievements")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void administratorCanListAchievements() throws Exception {
        when(adminAchievementService.listAll()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/admin/achievements"))
                .andExpect(status().isOk());
    }
}
