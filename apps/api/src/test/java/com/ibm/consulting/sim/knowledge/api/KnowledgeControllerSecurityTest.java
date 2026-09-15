package com.ibm.consulting.sim.knowledge.api;

import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.identity.infrastructure.SecurityConfig;
import com.ibm.consulting.sim.knowledge.application.KnowledgeIngestionService;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KnowledgeController.class)
@Import(SecurityConfig.class)
class KnowledgeControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private KnowledgeIngestionService ingestionService;
    @MockBean private ScenarioRepository scenarioRepository;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserRepository userRepository;

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/scenarios/{id}/documents", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "LEARNER")
    void learnerCannotListDocuments() throws Exception {
        mockMvc.perform(get("/api/v1/admin/scenarios/{id}/documents", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SCENARIO_AUTHOR")
    void update_rejectedWhenScenarioIsPublished() throws Exception {
        UUID scenarioId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        Scenario published = Scenario.create("Title", "Retail", "Desc", 3);
        published.publish(); // DRAFT -> ACTIVE
        when(scenarioRepository.findById(scenarioId)).thenReturn(Optional.of(published));

        mockMvc.perform(put("/api/v1/admin/scenarios/{id}/documents/{docId}", scenarioId, documentId)
                        .contentType("application/json")
                        .content("""
                                {"personaId":null,"collection":"SCENARIO_TRUTH","title":"x","content":"y"}
                                """))
                .andExpect(status().isUnprocessableEntity()); // ScenarioContentLockedException -> 422
    }

    @Test
    @WithMockUser(roles = "SCENARIO_AUTHOR")
    void update_rejectedForCrossScenarioDocument() throws Exception {
        UUID scenarioAId = UUID.randomUUID();
        UUID scenarioBsDocumentId = UUID.randomUUID();

        Scenario draft = Scenario.create("Title", "Retail", "Desc", 3); // stays DRAFT
        when(scenarioRepository.findById(scenarioAId)).thenReturn(Optional.of(draft));
        doThrow(new NotFoundException("KnowledgeDocument", scenarioBsDocumentId))
        .when(ingestionService)
        .update(eq(scenarioAId), eq(scenarioBsDocumentId), any(), any(), any(), any());

        mockMvc.perform(put("/api/v1/admin/scenarios/{id}/documents/{docId}", scenarioAId, scenarioBsDocumentId)
                        .contentType("application/json")
                        .content("""
                                {"personaId":null,"collection":"SCENARIO_TRUTH","title":"x","content":"y"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void administratorCanListDocuments() throws Exception {
        UUID scenarioId = UUID.randomUUID();
        when(ingestionService.list(scenarioId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/scenarios/{id}/documents", scenarioId))
                .andExpect(status().isOk());
    }
}