package com.ibm.consulting.sim.identity.api;

import com.ibm.consulting.sim.identity.application.AuthenticateUseCase;
import com.ibm.consulting.sim.identity.application.EmailVerificationService;
import com.ibm.consulting.sim.identity.application.PasswordResetService;
import com.ibm.consulting.sim.identity.application.RegisterUserUseCase;
import com.ibm.consulting.sim.identity.application.RegistrationResponse;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerValidationTest {

    @Autowired MockMvc mockMvc;
    @MockBean RegisterUserUseCase registerUserUseCase;
    @MockBean AuthenticateUseCase authenticateUseCase;
    @MockBean EmailVerificationService emailVerificationService;
    @MockBean PasswordResetService passwordResetService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    @Test
    void rejectsDisplayNameThatIsTooShortAfterNormalisation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(registrationJson("StrongPass123!", " A ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.displayName").exists());

        verifyNoInteractions(registerUserUseCase);
    }

    @Test
    void forwardsTheNormalisedDisplayNameForValidRegistration() throws Exception {
        when(registerUserUseCase.execute("learner@example.com", "StrongPass123!", "Alice Example"))
                .thenReturn(new RegistrationResponse("learner@example.com", true));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(registrationJson("StrongPass123!", "  Alice Example  ")))
                .andExpect(status().isCreated());

        verify(registerUserUseCase).execute("learner@example.com", "StrongPass123!", "Alice Example");
    }

    @Test
    void returnsOneValidationProblemWhenPasswordViolatesMultipleConstraints() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(registrationJson("", "Valid Name")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://consulting-sim.ibm.com/problems/validation-error"))
                .andExpect(jsonPath("$.violations.password").exists());

        verifyNoInteractions(registerUserUseCase);
    }

    private String registrationJson(String password, String displayName) {
        return """
                {
                  "email": "learner@example.com",
                  "password": "%s",
                  "displayName": "%s"
                }
                """.formatted(password, displayName);
    }
}
