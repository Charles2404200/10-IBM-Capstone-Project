package com.ibm.consulting.sim.scenario.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.identity.infrastructure.SecurityConfig;
import com.ibm.consulting.sim.scenario.application.*;
import com.ibm.consulting.sim.scenario.domain.ScenarioLifecycleDefinition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminScenarioLifecycleController.class)
@Import(SecurityConfig.class)
class AdminScenarioLifecycleControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean ScenarioLifecycleService service;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;
    private final UUID id = UUID.randomUUID();

    @ParameterizedTest
    @ValueSource(strings = {"SCENARIO_AUTHOR", "ADMINISTRATOR"})
    void authorsCanReadAndUpdate(String role) throws Exception {
        var view = new ScenarioLifecycleView(ScenarioLifecycleDefinition.defaults(), 2L);
        when(service.get(id)).thenReturn(view);
        when(service.update(eq(id), any())).thenReturn(view);
        mvc.perform(get(path()).with(user("author").roles(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.definition.stages.length()").value(10));
        mvc.perform(put(path()).with(user("author").roles(role)).contentType("application/json")
                        .content(mapper.writeValueAsString(new UpdateScenarioLifecycleRequest(view.definition(), 1L))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "REVIEWER"})
    void otherRolesCannotReadOrUpdate(String role) throws Exception {
        mvc.perform(get(path()).with(user("learner").roles(role))).andExpect(status().isForbidden());
        mvc.perform(put(path()).with(user("learner").roles(role)).contentType("application/json")
                        .content(mapper.writeValueAsString(new UpdateScenarioLifecycleRequest(ScenarioLifecycleDefinition.defaults(), 1L))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void conflictUsesProblemDetails() throws Exception {
        when(service.update(eq(id), any())).thenThrow(new ScenarioLifecycleConflictException());
        mvc.perform(put(path()).with(user("author").roles("SCENARIO_AUTHOR")).contentType("application/json")
                        .content(mapper.writeValueAsString(new UpdateScenarioLifecycleRequest(ScenarioLifecycleDefinition.defaults(), 1L))))
                .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void versionAndDefinitionAreMandatory() throws Exception {
        mvc.perform(put(path()).with(user("author").roles("SCENARIO_AUTHOR")).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void deeplyNestedAndOversizedPayloadsAreRejectedBeforeTheService() throws Exception {
        String nested = "{\"definition\":" + "[".repeat(30) + "0" + "]".repeat(30) + ",\"version\":1}";
        mvc.perform(put(path()).with(user("author").roles("SCENARIO_AUTHOR")).contentType("application/json").content(nested))
                .andExpect(status().isBadRequest());
        String oversized = "{\"description\":\"" + "x".repeat(LifecycleRequestBodyAdvice.MAX_REQUEST_BYTES) + "\"}";
        mvc.perform(put(path()).with(user("author").roles("SCENARIO_AUTHOR")).contentType("application/json").content(oversized))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private String path() { return "/api/v1/admin/scenarios/" + id + "/lifecycle"; }
}
