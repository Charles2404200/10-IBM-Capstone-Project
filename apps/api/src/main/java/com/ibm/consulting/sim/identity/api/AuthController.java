package com.ibm.consulting.sim.identity.api;

import com.ibm.consulting.sim.identity.application.AuthenticateUseCase;
import com.ibm.consulting.sim.identity.application.EmailVerificationService;
import com.ibm.consulting.sim.identity.application.PasswordResetService;
import com.ibm.consulting.sim.identity.application.RegistrationResponse;
import com.ibm.consulting.sim.identity.application.RegisterUserUseCase;
import com.ibm.consulting.sim.identity.application.TokenResponse;
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

    public AuthController(RegisterUserUseCase registerUseCase, AuthenticateUseCase authenticateUseCase,
                          EmailVerificationService emailVerificationService,
                          PasswordResetService passwordResetService) {
        this.registerUseCase = registerUseCase;
        this.authenticateUseCase = authenticateUseCase;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
    }

    record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(min = 2, max = 80) String displayName) {}

    record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    record EmailRequest(@NotBlank @Email String email) {}
    record TokenRequest(@NotBlank String token) {}
    record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 8, max = 128) String password) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegistrationResponse register(@Valid @RequestBody RegisterRequest req) {
        return registerUseCase.execute(req.email(), req.password(), req.displayName());
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return authenticateUseCase.execute(req.email(), req.password());
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
