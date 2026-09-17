package com.ibm.consulting.sim.identity.api;

import com.ibm.consulting.sim.identity.application.AuthenticateUseCase;
import com.ibm.consulting.sim.identity.application.EmailVerificationService;
import com.ibm.consulting.sim.identity.application.PasswordResetService;
import com.ibm.consulting.sim.identity.application.RegisterUserUseCase;
import com.ibm.consulting.sim.identity.application.RegistrationResponse;
import com.ibm.consulting.sim.identity.application.LoginAttemptLimiter;
import com.ibm.consulting.sim.identity.application.TokenResponse;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.shared.email.application.EmailDeliveryUnavailableException;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerValidationTest {

    @Autowired MockMvc mockMvc;
    @MockBean RegisterUserUseCase registerUserUseCase;
    @MockBean AuthenticateUseCase authenticateUseCase;
    @MockBean EmailVerificationService emailVerificationService;
    @MockBean PasswordResetService passwordResetService;
    @MockBean LoginAttemptLimiter loginAttemptLimiter;
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

    @Test
    void acceptsMaximumSizedLoginFields() throws Exception {
        String email = maximumLengthEmail();
        String password = "P".repeat(128);
        when(authenticateUseCase.execute(email, password))
                .thenReturn(new TokenResponse("token", "user-id", "Learner", "LEARNER", false));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk());

        verify(authenticateUseCase).execute(email, password);
    }

    @Test
    void rejectsOversizedLoginPasswordBeforeAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"learner@example.com\",\"password\":\"%s\"}"
                                .formatted("P".repeat(129))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.password").exists());

        verifyNoInteractions(authenticateUseCase);
    }

    @Test
    void boundsEveryPublicEmailFieldAtThePersistedColumnLength() throws Exception {
        String maximumEmail = maximumLengthEmail();
        when(registerUserUseCase.execute(maximumEmail, "StrongPass123!", "Valid Name"))
                .thenReturn(new RegistrationResponse(maximumEmail, true));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"StrongPass123!\",\"displayName\":\"Valid Name\"}"
                                .formatted(maximumEmail)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/email-verification/resend")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\"}".formatted(maximumEmail)))
                .andExpect(status().isAccepted());

        String oversizedEmail = oversizedEmail();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"StrongPass123!\",\"displayName\":\"Valid Name\"}"
                                .formatted(oversizedEmail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.email").exists());
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\"}".formatted(oversizedEmail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.email").exists());
        verify(registerUserUseCase).execute(maximumEmail, "StrongPass123!", "Valid Name");
        verify(emailVerificationService).resend(maximumEmail);
        verifyNoInteractions(passwordResetService);
    }

    @Test
    void acceptsMaximumCredentialTokensAndRejectsOversizedValuesBeforeUseCases() throws Exception {
        String maximumToken = "t".repeat(128);
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType("application/json")
                        .content("{\"token\":\"%s\"}".formatted(maximumToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType("application/json")
                        .content("{\"token\":\"%s\",\"password\":\"StrongPass123!\"}".formatted(maximumToken)))
                .andExpect(status().isNoContent());

        String oversizedToken = "t".repeat(129);
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType("application/json")
                        .content("{\"token\":\"%s\"}".formatted(oversizedToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.token").exists());
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType("application/json")
                        .content("{\"token\":\"%s\",\"password\":\"StrongPass123!\"}".formatted(oversizedToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.token").exists());
        verify(emailVerificationService).verify(maximumToken);
        verify(passwordResetService).reset(maximumToken, "StrongPass123!");
    }

    @Test
    void passwordResetRequestKeepsItsAcceptedSuccessContract() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"learner@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        verify(passwordResetService).request("learner@example.com");
    }

    @Test
    void emailDeliveryFailureReturnsSafeRetriableServiceUnavailableProblem() throws Exception {
        doThrow(new EmailDeliveryUnavailableException(
                "SMTP smtp.internal:587 user=mailer recipients=learner@example.com secret=top-secret"))
                .when(passwordResetService).request("learner@example.com");

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"learner@example.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("Service Unavailable"))
                .andExpect(jsonPath("$.type")
                        .value("https://consulting-sim.ibm.com/problems/email-delivery-unavailable"))
                .andExpect(jsonPath("$.detail")
                        .value("We could not send email right now. Please try again shortly."))
                .andExpect(content().string(not(containsString("smtp.internal"))))
                .andExpect(content().string(not(containsString("mailer"))))
                .andExpect(content().string(not(containsString("learner@example.com"))))
                .andExpect(content().string(not(containsString("top-secret"))));
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

    private String maximumLengthEmail() {
        return "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(58) + ".com";
    }

    private String oversizedEmail() {
        return "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(59) + ".com";
    }
}
