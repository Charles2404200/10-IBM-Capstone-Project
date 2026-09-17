package com.ibm.consulting.sim.scenario.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.identity.infrastructure.SecurityConfig;
import com.ibm.consulting.sim.scenario.application.ScenarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminScenarioController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "SCENARIO_AUTHOR")
class AdminScenarioContractValidationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ScenarioService scenarioService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    private final UUID scenarioId = UUID.randomUUID();

    static Stream<Arguments> numericDifficultyRanges() {
        return Stream.of(
                Arguments.of("researchArtifactsPerAction", 2, 8),
                Arguments.of("distractorArtifactsPerAction", 0, 7),
                Arguments.of("contradictionCount", 0, 6),
                Arguments.of("initialTrust", 0, 100),
                Arguments.of("initialInterest", 0, 100),
                Arguments.of("initialPatience", 0, 100),
                Arguments.of("meetingTurnLimit", 4, 20),
                Arguments.of("timelinePressureDays", 1, 90),
                Arguments.of("requiredEvidenceCount", 2, 8),
                Arguments.of("requiredConfidencePercent", 20, 90),
                Arguments.of("outreachAcceptanceThreshold", 50, 95),
                Arguments.of("proposalEvidenceCoverageThreshold", 30, 95),
                Arguments.of("personaResistance", 0, 100),
                Arguments.of("scoringTolerance", 70, 130));
    }

    @ParameterizedTest(name = "{0} accepts [{1}, {2}] and rejects outside/null values")
    @MethodSource("numericDifficultyRanges")
    void validatesEveryNumericDifficultyBoundary(String field, int minimum, int maximum) throws Exception {
        Map<String, Object> minimumProfile = validProfile();
        minimumProfile.put(field, minimum);
        adjustDependentFields(field, minimum, minimumProfile);
        expectDifficultyStatus(minimumProfile, 200);

        Map<String, Object> maximumProfile = validProfile();
        maximumProfile.put(field, maximum);
        adjustDependentFields(field, maximum, maximumProfile);
        expectDifficultyStatus(maximumProfile, 200);

        Map<String, Object> belowMinimum = validProfile();
        belowMinimum.put(field, minimum - 1);
        expectDifficultyStatus(belowMinimum, 400);

        Map<String, Object> aboveMaximum = validProfile();
        aboveMaximum.put(field, maximum + 1);
        expectDifficultyStatus(aboveMaximum, 400);

        Map<String, Object> missingValue = validProfile();
        missingValue.put(field, null);
        expectDifficultyStatus(missingValue, 400);
    }

    @Test
    void rejectsMissingEnumBooleanAndProfileValues() throws Exception {
        Map<String, Object> missingLevel = validProfile();
        missingLevel.put("level", null);
        expectDifficultyStatus(missingLevel, 400);

        Map<String, Object> missingBudgetVisibility = validProfile();
        missingBudgetVisibility.put("budgetVisible", null);
        expectDifficultyStatus(missingBudgetVisibility, 400);

        mockMvc.perform(put(difficultyPath()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(put(difficultyPath()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("profile", Map.of("level", "IMPOSSIBLE")))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsDistractorsThatAreNotLessThanResearchArtifacts() throws Exception {
        Map<String, Object> equalCounts = validProfile();
        equalCounts.put("researchArtifactsPerAction", 4);
        equalCounts.put("distractorArtifactsPerAction", 4);

        expectDifficultyStatus(equalCounts, 400);
    }

    @Test
    void validatesRubricEntriesAndTotal() throws Exception {
        expectRubricStatus(mapOf("Communication", null, "Consulting", 100), 400);
        expectRubricStatus(Map.of("Communication", -20, "Consulting", 120), 400);
        expectRubricStatus(Map.of("Communication", 101), 400);
        expectRubricStatus(Map.of("", 100), 400);
        expectRubricStatus(Map.of(), 400);
        expectRubricStatus(Map.of("Communication", 49, "Consulting", 50), 400);
        expectRubricStatus(Map.of("Communication", 51, "Consulting", 50), 400);
        expectRubricStatus(Map.of("Communication", 0, "Consulting", 100), 200);
        expectRubricStatus(Map.of("Communication", 25, "Consulting", 75), 200);
    }

    @Test
    void validatesEveryNestedAuthoringConfigElementBeforeDomainMapping() throws Exception {
        expectAuthoringConfigStatus("""
                {"config":{"canonicalFacts":null,"revealRules":[]}}
                """, 400);
        expectAuthoringConfigStatus("""
                {"config":{"canonicalFacts":[],"revealRules":null}}
                """, 400);
        expectAuthoringConfigStatus("""
                {"config":{"canonicalFacts":[null],"revealRules":[]}}
                """, 400);
        expectAuthoringConfigStatus("""
                {"config":{"canonicalFacts":[],"revealRules":[null]}}
                """, 400);
        expectAuthoringConfigStatus("""
                {"config":{"canonicalFacts":[{"label":"Constraint","value":"Capped","evidenceType":"FINANCIAL_SIGNAL","availableInResearch":true}],"revealRules":[]}}
                """, 400);
        expectAuthoringConfigStatus("""
                {"config":{"canonicalFacts":[{"id":" ","label":"Constraint","value":"Capped","evidenceType":"FINANCIAL_SIGNAL","availableInResearch":true}],"revealRules":[]}}
                """, 400);
        expectAuthoringConfigStatus("""
                {"config":{"canonicalFacts":[{"id":"budget","label":"Constraint","value":"Capped","evidenceType":"NOT_REAL","availableInResearch":true}],"revealRules":[]}}
                """, 400);
        expectAuthoringConfigStatus("""
                {"config":{"canonicalFacts":[],"revealRules":[{"requiredEvidenceTypes":["FINANCIAL_SIGNAL"],"minimumEvidenceCount":1}]}}
                """, 400);
        expectAuthoringConfigStatus("""
                {"config":{"canonicalFacts":[{"id":"budget","label":"Constraint","value":"Capped","evidenceType":"FINANCIAL_SIGNAL","availableInResearch":true}],"revealRules":[{"target":"BUDGET_SIGNAL","requiredEvidenceTypes":["FINANCIAL_SIGNAL"],"minimumEvidenceCount":1}]}}
                """, 200);
    }

    @Test
    void acceptsNullableIntelligenceFieldsWhileEditingADraftLead() throws Exception {
        UUID leadId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/admin/scenarios/{scenarioId}/leads/{leadId}", scenarioId, leadId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName":"Example Corp",
                                  "industry":"Technology",
                                  "publicDescription":null,
                                  "difficulty":"MEDIUM",
                                  "potentialValueRange":null,
                                  "decisionMaker":null,
                                  "technologyStack":null,
                                  "budgetSignal":null,
                                  "painSeverity":null,
                                  "signals":[]
                                }
                                """))
                .andExpect(status().isOk());
    }

    private void expectDifficultyStatus(Map<String, Object> profile, int expectedStatus) throws Exception {
        var result = mockMvc.perform(put(difficultyPath())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("profile", profile))));
        if (expectedStatus == 200) result.andExpect(status().isOk());
        else result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    private void expectRubricStatus(Map<String, Integer> weights, int expectedStatus) throws Exception {
        var result = mockMvc.perform(put("/api/v1/admin/scenarios/{scenarioId}/rubric", scenarioId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("weights", weights))));
        if (expectedStatus == 200) result.andExpect(status().isOk());
        else result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    private void expectAuthoringConfigStatus(String json, int expectedStatus) throws Exception {
        var result = mockMvc.perform(put("/api/v1/admin/scenarios/{scenarioId}/authoring-config", scenarioId)
                .contentType(MediaType.APPLICATION_JSON).content(json));
        if (expectedStatus == 200) result.andExpect(status().isOk());
        else result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private String difficultyPath() {
        return "/api/v1/admin/scenarios/" + scenarioId + "/difficulty-profile";
    }

    private static Map<String, Object> validProfile() {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("level", "MEDIUM");
        profile.put("researchArtifactsPerAction", 5);
        profile.put("distractorArtifactsPerAction", 1);
        profile.put("contradictionCount", 1);
        profile.put("initialTrust", 50);
        profile.put("initialInterest", 50);
        profile.put("initialPatience", 50);
        profile.put("meetingTurnLimit", 14);
        profile.put("budgetVisible", false);
        profile.put("timelinePressureDays", 18);
        profile.put("requiredEvidenceCount", 3);
        profile.put("requiredConfidencePercent", 60);
        profile.put("outreachAcceptanceThreshold", 75);
        profile.put("proposalEvidenceCoverageThreshold", 65);
        profile.put("personaResistance", 50);
        profile.put("scoringTolerance", 100);
        return profile;
    }

    private static void adjustDependentFields(String field, int value, Map<String, Object> profile) {
        if (field.equals("researchArtifactsPerAction") && value == 2) {
            profile.put("distractorArtifactsPerAction", 1);
        }
        if (field.equals("distractorArtifactsPerAction") && value == 7) {
            profile.put("researchArtifactsPerAction", 8);
        }
    }

    private static Map<String, Integer> mapOf(String firstKey, Integer firstValue,
                                               String secondKey, Integer secondValue) {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put(firstKey, firstValue);
        values.put(secondKey, secondValue);
        return values;
    }
}
