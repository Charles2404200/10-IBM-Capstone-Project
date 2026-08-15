package com.ibm.consulting.sim.identity.api;

import com.ibm.consulting.sim.identity.application.ForgotPasswordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/forgot-password")
public class ForgotPasswordController {

    private final ForgotPasswordService forgotPasswordService;

    public ForgotPasswordController(
            ForgotPasswordService forgotPasswordService
    ) {
        this.forgotPasswordService = forgotPasswordService;
    }

    record VerifyMailRequest(
            @NotBlank
            @Email
            String email
    ) {}

    record VerifyOtpRequest(
            @NotNull
            @Min(100000)
            @Max(999999)
            Integer otp,

            @NotBlank
            @Email
            String email
    ) {}

    record VerifyOtpResponse(
            String message,
            String resetToken
    ) {}

    record ChangePasswordRequest(
            @NotBlank
            String resetToken,

            @NotBlank
            @Size(min = 8, max = 128)
            String password,

            @NotBlank
            @Size(min = 8, max = 128)
            String repeatPassword
    ) {}

    record MessageResponse(
            String message
    ) {}

    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyMail(
            @Valid @RequestBody VerifyMailRequest request
    ) {

        forgotPasswordService.verifyEmail(
                request.email()
        );

        return ResponseEntity.ok(
                new MessageResponse(
                        "If an account exists for this email, a password reset OTP has been sent."
                )
        );
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<VerifyOtpResponse> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request
    ) {

        String resetToken =
                forgotPasswordService.verifyOtp(
                        request.otp(),
                        request.email()
                );

        return ResponseEntity.ok(
                new VerifyOtpResponse(
                        "OTP verified successfully",
                        resetToken
                )
        );
    }

    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request
    ) {

        forgotPasswordService.changePassword(
                request.resetToken(),
                request.password(),
                request.repeatPassword()
        );

        return ResponseEntity.ok(
                new MessageResponse(
                        "Password changed successfully"
                )
        );
    }
}