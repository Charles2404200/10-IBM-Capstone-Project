package com.ibm.consulting.sim.achievement.api;

import com.ibm.consulting.sim.achievement.application.AchievementAdminView;
import com.ibm.consulting.sim.achievement.application.AdminAchievementService;
import com.ibm.consulting.sim.achievement.application.ConditionNode;
import com.ibm.consulting.sim.achievement.domain.ConditionType;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.identity.infrastructure.SecurityConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAchievementController.class)
@Import(SecurityConfig.class)
class AdminAchievementControllerContractTest {

    @Autowired MockMvc mockMvc;
    @MockBean AdminAchievementService achievementService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    static Stream<String> malformedRules() {
        return Stream.of(
                "{\"kind\":\"GROUP\",\"operator\":\"AND\",\"children\":[]}",
                "{\"kind\":\"GROUP\",\"children\":[{\"kind\":\"LEAF\",\"type\":\"MIN_ENGAGEMENTS_WON\",\"threshold\":1}]}",
                "{\"kind\":\"GROUP\",\"operator\":\"AND\",\"children\":[null]}",
                "{\"kind\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"kind\":\"LEAF\",\"threshold\":1}]}",
                "{\"kind\":\"LEAF\",\"threshold\":80}",
                "{\"kind\":\"LEAF\",\"type\":\"MIN_BEST_OVERALL_SCORE\"}",
                "{\"kind\":\"LEAF\",\"type\":\"MIN_COMPETENCY_SCORE\",\"threshold\":80}",
                "{\"kind\":\"LEAF\",\"type\":\"MIN_ENGAGEMENTS_WON\",\"threshold\":-1}",
                "{\"kind\":\"LEAF\",\"type\":\"MIN_BEST_OVERALL_SCORE\",\"threshold\":101}"
        );
    }

    @ParameterizedTest
    @MethodSource("malformedRules")
    @WithMockUser(roles = "ADMINISTRATOR")
    void malformedRuleTreesReturnValidationProblemInsteadOfServerError(String rule) throws Exception {
        mockMvc.perform(post("/api/v1/admin/achievements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rule contract\",\"rule\":" + rule + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.violations").isMap());

        verifyNoInteractions(achievementService);
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void optionalDescriptionCanBeOmittedAndSerializesAsNull() throws Exception {
        ConditionNode rule = ConditionNode.leaf(ConditionType.MIN_ENGAGEMENTS_WON, null, 1);
        UUID achievementId = UUID.randomUUID();
        when(achievementService.create(any())).thenReturn(
                new AchievementAdminView(achievementId, "First win", null, "trophy", true, rule));
        when(achievementService.update(eq(achievementId), any())).thenReturn(
                new AchievementAdminView(achievementId, "First win", null, "trophy", true, rule));

        String request = """
                {"name":"First win","iconKey":"trophy","rule":
                  {"kind":"LEAF","type":"MIN_ENGAGEMENTS_WON","threshold":1}}
                """;

        mockMvc.perform(post("/api/v1/admin/achievements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(put("/api/v1/admin/achievements/{id}", achievementId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.nullValue()));
    }
}
