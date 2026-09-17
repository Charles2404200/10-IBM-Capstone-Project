package com.ibm.consulting.sim.identity.infrastructure;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiSecurityContractTest.ContractEndpoints.class)
@Import({SecurityConfig.class, ApiSecurityContractTest.ContractEndpoints.class})
class ApiSecurityContractTest {

    @Autowired MockMvc mockMvc;
    @MockBean JwtTokenProvider tokens;
    @MockBean UserRepository users;

    @BeforeEach
    void resetInvocations() {
        ContractEndpoints.INVOCATIONS.set(0);
    }

    @Test
    void representativeLearnerRoutesRequireAuthenticationWithoutInvokingControllers() throws Exception {
        for (String route : List.of(
                "/api/v1/engagements", "/api/v1/engagements/00000000-0000-0000-0000-000000000001/research",
                "/api/v1/engagements/00000000-0000-0000-0000-000000000001/outreach",
                "/api/v1/engagements/00000000-0000-0000-0000-000000000001/preparation",
                "/api/v1/meetings/00000000-0000-0000-0000-000000000001/messages",
                "/api/v1/engagements/00000000-0000-0000-0000-000000000001/proposal/review",
                "/api/v1/engagements/00000000-0000-0000-0000-000000000001/assessment",
                "/api/v1/portfolio/summary")) {
            mockMvc.perform(get(route))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.title").value("Unauthorized"))
                    .andExpect(jsonPath("$.detail").value("Authentication required"))
                    .andExpect(content().string(not(containsString("00000000-0000"))))
                    .andExpect(content().string(not(containsString("stackTrace"))));
        }
        assertThat(ContractEndpoints.INVOCATIONS).hasValue(0);
    }

    @Test
    void documentedPublicRoutesRemainPublic() throws Exception {
        mockMvc.perform(get("/api/v1/scenarios"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/ping"))
                .andExpect(status().isOk());
        assertThat(ContractEndpoints.INVOCATIONS).hasValue(2);
    }

    @Test
    void unusableBearerTokensShareOneSafeUnauthorizedContract() throws Exception {
        UUID userId = UUID.randomUUID();
        User disabled = mock(User.class);
        when(disabled.isActive()).thenReturn(false);
        when(tokens.extractUserId("missing-user")).thenReturn(userId);
        when(users.findById(userId)).thenReturn(Optional.empty(), Optional.of(disabled));
        when(tokens.extractUserId("disabled-user")).thenReturn(userId);
        User active = mock(User.class);
        when(active.isActive()).thenReturn(true);
        when(tokens.extractUserId("invalidated")).thenReturn(userId);
        when(users.findById(userId)).thenReturn(Optional.empty(), Optional.of(disabled), Optional.of(active));
        when(tokens.isValidForUser("invalidated", active)).thenReturn(false);
        when(tokens.extractUserId("malformed")).thenThrow(new JwtException("parser secret detail"));
        when(tokens.extractUserId("expired")).thenThrow(new JwtException("expired at internal clock"));
        when(tokens.extractUserId("bad-signature")).thenThrow(new JwtException("signature key detail"));
        when(tokens.extractUserId("")).thenThrow(new IllegalArgumentException("empty"));

        for (String header : List.of("Basic abc", "Bearer ", "Bearer malformed", "Bearer expired",
                "Bearer bad-signature", "Bearer missing-user", "Bearer disabled-user", "Bearer invalidated")) {
            mockMvc.perform(get("/api/v1/engagements").header("Authorization", header))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.detail").value("Authentication required"))
                    .andExpect(content().string(not(containsString(header))))
                    .andExpect(content().string(not(containsString("JwtException"))))
                    .andExpect(content().string(not(containsString("secret"))))
                    .andExpect(content().string(not(containsString("stackTrace"))));
        }
        assertThat(ContractEndpoints.INVOCATIONS).hasValue(0);
    }

    @Test
    void anonymousAndLearnerAreDeniedReviewerAndAdminRoutesWithoutInvocation() throws Exception {
        mockMvc.perform(get("/api/v1/reviewer/contract"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/contract"))
                .andExpect(status().isUnauthorized());
        assertThat(ContractEndpoints.INVOCATIONS).hasValue(0);
    }

    @Test
    @WithMockUser(roles = "LEARNER")
    void insufficientRoleReceivesSafeForbiddenProblem() throws Exception {
        for (String route : List.of("/api/v1/reviewer/contract", "/api/v1/admin/contract")) {
            mockMvc.perform(get(route))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Forbidden"))
                    .andExpect(jsonPath("$.detail").value("Access denied"))
                    .andExpect(content().string(not(containsString("ROLE_LEARNER"))))
                    .andExpect(content().string(not(containsString("stackTrace"))));
        }
        assertThat(ContractEndpoints.INVOCATIONS).hasValue(0);
    }

    @Test
    @WithMockUser(roles = "REVIEWER")
    void reviewerRoleReachesReviewerController() throws Exception {
        mockMvc.perform(get("/api/v1/reviewer/contract")).andExpect(status().isOk());
        assertThat(ContractEndpoints.INVOCATIONS).hasValue(1);
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void administratorRoleReachesAdminController() throws Exception {
        mockMvc.perform(get("/api/v1/admin/contract")).andExpect(status().isOk());
        assertThat(ContractEndpoints.INVOCATIONS).hasValue(1);
    }

    @RestController
    static class ContractEndpoints {
        static final AtomicInteger INVOCATIONS = new AtomicInteger();

        @GetMapping("/api/v1/scenarios") String scenarios() { INVOCATIONS.incrementAndGet(); return "public"; }
        @PostMapping("/api/v1/auth/ping") void auth() { INVOCATIONS.incrementAndGet(); }
        @GetMapping("/api/v1/engagements") String engagements() { INVOCATIONS.incrementAndGet(); return "private"; }
        @PreAuthorize("hasRole('REVIEWER')")
        @GetMapping("/api/v1/reviewer/contract") String reviewer() { INVOCATIONS.incrementAndGet(); return "reviewer"; }
        @PreAuthorize("hasRole('ADMINISTRATOR')")
        @GetMapping("/api/v1/admin/contract") String admin() { INVOCATIONS.incrementAndGet(); return "admin"; }
    }
}
