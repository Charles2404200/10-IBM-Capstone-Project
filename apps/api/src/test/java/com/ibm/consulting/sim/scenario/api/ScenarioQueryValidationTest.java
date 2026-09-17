package com.ibm.consulting.sim.scenario.api;

import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.scenario.application.ScenarioCatalogResponse;
import com.ibm.consulting.sim.scenario.application.ScenarioService;
import com.ibm.consulting.sim.scenario.domain.ScenarioCatalogQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScenarioController.class)
@AutoConfigureMockMvc(addFilters = false)
class ScenarioQueryValidationTest {
    @Autowired MockMvc mockMvc;
    @MockBean ScenarioService scenarios;
    @MockBean JwtTokenProvider tokens;
    @MockBean UserRepository users;

    @Test
    void catalogueRejectsOutOfRangeAndNonNumericParametersBeforeServiceInvocation() throws Exception {
        for (String query : List.of("?page=-1", "?size=0", "?size=25", "?difficulty=0", "?difficulty=6",
                "?page=abc", "?size=abc", "?difficulty=abc")) {
            mockMvc.perform(get("/api/v1/scenarios/catalog" + query))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400));
        }
        verifyNoInteractions(scenarios);
    }

    @Test
    void boundaryValuesAndOmittedParametersPreserveTheEstablishedContract() throws Exception {
        when(scenarios.listCatalog(any())).thenReturn(new ScenarioCatalogResponse(List.of(), 0, 0, 9, 0));

        mockMvc.perform(get("/api/v1/scenarios/catalog").param("page", "0").param("size", "24")
                        .param("difficulty", "5"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/scenarios/catalog"))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(ScenarioCatalogQuery.class);
        verify(scenarios, times(2)).listCatalog(captor.capture());
        assertThat(captor.getAllValues().get(0)).isEqualTo(new ScenarioCatalogQuery(null, null, 5, 0, 24));
        assertThat(captor.getAllValues().get(1)).isEqualTo(new ScenarioCatalogQuery(null, null, null, 0, 9));
    }
}
