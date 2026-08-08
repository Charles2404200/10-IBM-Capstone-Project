package com.ibm.consulting.sim.identity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.identity.application.AdminUserService;
import com.ibm.consulting.sim.identity.application.UserSummary;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the admin user-management API enforces {@code ADMINISTRATOR}-only
 * access, regardless of what other authenticated role is calling it.
 */
@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
class AdminUserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminUserService adminUserService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "LEARNER")
    void learnerCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "REVIEWER")
    void reviewerCannotChangeRoles() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", UUID.randomUUID())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RoleBody(UserRole.SCENARIO_AUTHOR))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void administratorCanListUsers() throws Exception {
        when(adminUserService.listUsers()).thenReturn(
                List.of(new UserSummary(UUID.randomUUID(), "a@ibm.com", "A", UserRole.LEARNER, true)));

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk());
    }

    private record RoleBody(UserRole role) {}
}
