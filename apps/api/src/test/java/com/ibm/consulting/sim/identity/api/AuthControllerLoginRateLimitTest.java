package com.ibm.consulting.sim.identity.api;

import com.ibm.consulting.sim.identity.application.AuthenticateUseCase;
import com.ibm.consulting.sim.identity.application.CaffeineLoginAttemptStore;
import com.ibm.consulting.sim.identity.application.EmailVerificationService;
import com.ibm.consulting.sim.identity.application.LoginAttemptLimiter;
import com.ibm.consulting.sim.identity.application.LoginAttemptProperties;
import com.ibm.consulting.sim.identity.application.PasswordResetService;
import com.ibm.consulting.sim.identity.application.RegisterUserUseCase;
import com.ibm.consulting.sim.identity.application.TokenResponse;
import com.ibm.consulting.sim.identity.domain.InvalidCredentialsException;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.shared.api.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({LoginAttemptLimiter.class, CaffeineLoginAttemptStore.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(LoginAttemptProperties.class)
@TestPropertySource(properties = {
        "app.identity.login-attempts.max-failures=2",
        "app.identity.login-attempts.window=60s",
        "app.identity.login-attempts.maximum-tracked-accounts=100"
})
class AuthControllerLoginRateLimitTest {

    @Autowired MockMvc mockMvc;
    @MockBean RegisterUserUseCase registerUserUseCase;
    @MockBean AuthenticateUseCase authenticateUseCase;
    @MockBean EmailVerificationService emailVerificationService;
    @MockBean PasswordResetService passwordResetService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    @Test
    void repeatedFailuresReturn429WithoutBlockingAnotherAccount() throws Exception {
        when(authenticateUseCase.execute(anyString(), anyString())).thenThrow(new InvalidCredentialsException());

        attempt("target@example.com").andExpect(status().isUnprocessableEntity());
        attempt("target@example.com").andExpect(status().isUnprocessableEntity());
        attempt("target@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.type").value("https://consulting-sim.ibm.com/problems/login-rate-limit"));

        attempt("another@example.com").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unknownAndExistingIdentifiersReceiveTheSameFailureAndThrottleResponses() throws Exception {
        when(authenticateUseCase.execute(anyString(), anyString())).thenThrow(new InvalidCredentialsException());

        for (String email : new String[] {"known@example.com", "unknown@example.com"}) {
            attempt(email).andExpect(status().isUnprocessableEntity());
            attempt(email).andExpect(status().isUnprocessableEntity());
            attempt(email).andExpect(status().isTooManyRequests());
        }
    }

    @Test
    void successfulAuthenticationReturnsNormallyAndResetsPriorFailures() throws Exception {
        when(authenticateUseCase.execute(eq("recover@example.com"), anyString()))
                .thenThrow(new InvalidCredentialsException())
                .thenReturn(new TokenResponse("access-token", "user-id", "Learner", "LEARNER", false))
                .thenThrow(new InvalidCredentialsException());

        attempt("recover@example.com").andExpect(status().isUnprocessableEntity());
        attempt("recover@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
        attempt("recover@example.com").andExpect(status().isUnprocessableEntity());
        attempt("recover@example.com").andExpect(status().isUnprocessableEntity());
        attempt("recover@example.com").andExpect(status().isTooManyRequests());
    }

    private org.springframework.test.web.servlet.ResultActions attempt(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content("""
                        {"email":"%s","password":"WrongPassword123!"}
                        """.formatted(email)));
    }
}
