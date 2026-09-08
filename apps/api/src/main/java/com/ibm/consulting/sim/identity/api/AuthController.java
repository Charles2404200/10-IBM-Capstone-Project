package com.ibm.consulting.sim.identity.api;

import com.ibm.consulting.sim.identity.application.AuthenticateUseCase;
import com.ibm.consulting.sim.identity.application.EmailVerificationService;
import com.ibm.consulting.sim.identity.application.PasswordResetService;
import com.ibm.consulting.sim.identity.application.RegistrationResponse;
import com.ibm.consulting.sim.identity.application.RegisterUserUseCase;
import com.ibm.consulting.sim.identity.application.TokenResponse;
import com.ibm.consulting.sim.identity.application.LoginAttemptLimiter;
import com.ibm.consulting.sim.identity.domain.InvalidCredentialsException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterUserUseCase registerUseCase;
    private final AuthenticateUseCase authenticateUseCase;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthController(RegisterUserUseCase registerUseCase, AuthenticateUseCase authenticateUseCase,
                          EmailVerificationService emailVerificationService,
                          PasswordResetService passwordResetService,
                          LoginAttemptLimiter loginAttemptLimiter) {
        this.registerUseCase = registerUseCase;
        this.authenticateUseCase = authenticateUseCase;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    record RegisterRequest(
            @NotBlank @Email @Size(max = AuthenticationRequestLimits.EMAIL_MAX_LENGTH) String email,
            @NotBlank @Size(min = 8, max = AuthenticationRequestLimits.PASSWORD_MAX_LENGTH) String password,
            @NotBlank @Size(min = 2, max = 80) String displayName) {
        RegisterRequest {
            displayName = displayName == null ? null : displayName.trim();
        }
    }

    record LoginRequest(
            @NotBlank @Email @Size(max = AuthenticationRequestLimits.EMAIL_MAX_LENGTH) String email,
            @NotBlank @Size(max = AuthenticationRequestLimits.PASSWORD_MAX_LENGTH) String password) {}

    record EmailRequest(@NotBlank @Email @Size(max = AuthenticationRequestLimits.EMAIL_MAX_LENGTH) String email) {}
    record TokenRequest(@NotBlank @Size(max = AuthenticationRequestLimits.CREDENTIAL_TOKEN_MAX_LENGTH) String token) {}
    record ResetPasswordRequest(
            @NotBlank @Size(max = AuthenticationRequestLimits.CREDENTIAL_TOKEN_MAX_LENGTH) String token,
            @NotBlank @Size(min = 8, max = AuthenticationRequestLimits.PASSWORD_MAX_LENGTH) String password) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegistrationResponse register(@Valid @RequestBody RegisterRequest req) {
        return registerUseCase.execute(req.email(), req.password(), req.displayName());
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest req) {
        loginAttemptLimiter.checkAllowed(req.email());
        try {
            TokenResponse response = authenticateUseCase.execute(req.email(), req.password());
            loginAttemptLimiter.recordSuccess(req.email());
            return response;
        } catch (InvalidCredentialsException exception) {
            loginAttemptLimiter.recordFailure(req.email());
            throw exception;
        }
    }

    @PostMapping("/email-verification/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void resendVerification(@Valid @RequestBody EmailRequest req) {
        emailVerificationService.resend(req.email());
    }

    @PostMapping("/email-verification/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmVerification(@Valid @RequestBody TokenRequest req) {
        emailVerificationService.verify(req.token());
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void requestPasswordReset(@Valid @RequestBody EmailRequest req) {
        passwordResetService.request(req.email());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmPasswordReset(@Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.reset(req.token(), req.password());
    }
}
