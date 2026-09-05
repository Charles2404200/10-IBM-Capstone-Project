package com.ibm.consulting.sim.admin.api;

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

import com.ibm.consulting.sim.admin.application.PlatformOverviewResponse;
import com.ibm.consulting.sim.admin.application.PlatformOverviewService;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.identity.infrastructure.SecurityConfig;

@WebMvcTest(AdminPlatformController.class)
@Import(SecurityConfig.class)
class AdminPlatformControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlatformOverviewService overviewService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    // tests unauthorised access to the platform overview endpoint
    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/platform/overview"))
                .andExpect(status().isUnauthorized());
    }

    // tests forbidden backend response for roles without platform overview access
    @Test
    @WithMockUser(roles = "REVIEWER")
    void reviewerCannotViewPlatformOverview() throws Exception {
        mockMvc.perform(get("/api/v1/admin/platform/overview"))
                .andExpect(status().isForbidden());
    }

    // tests forbidden backend response for roles without platform overview access
    @Test
    @WithMockUser(roles = "SCENARIO_AUTHOR")
    void scenarioAuthorCannotViewPlatformOverview() throws Exception {
        mockMvc.perform(get("/api/v1/admin/platform/overview"))
                .andExpect(status().isForbidden());
    }

    // tests successful backend response for administrator role
    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void administratorCanViewPlatformOverview() throws Exception {
        when(overviewService.getOverview()).thenReturn(new PlatformOverviewResponse(0, 0, 0, 0, null, Map.of(), Map.of(), List.of()));

        mockMvc.perform(get("/api/v1/admin/platform/overview"))
                .andExpect(status().isOk());
    }
}
