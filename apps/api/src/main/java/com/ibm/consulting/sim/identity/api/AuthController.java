package com.ibm.consulting.sim.identity.api;

import com.ibm.consulting.sim.identity.application.AuthenticateUseCase;
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

    public AuthController(RegisterUserUseCase registerUseCase, AuthenticateUseCase authenticateUseCase) {
        this.registerUseCase = registerUseCase;
        this.authenticateUseCase = authenticateUseCase;
    }

    record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(min = 2, max = 80) String displayName) {}

    record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    TokenResponse register(@Valid @RequestBody RegisterRequest req) {
        return registerUseCase.execute(req.email(), req.password(), req.displayName());
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return authenticateUseCase.execute(req.email(), req.password());
    }
}
