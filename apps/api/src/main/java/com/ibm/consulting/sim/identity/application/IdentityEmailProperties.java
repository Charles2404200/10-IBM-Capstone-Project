package com.ibm.consulting.sim.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Identity-specific TTLs and public UI links; delivery settings live in shared email config. */
@ConfigurationProperties(prefix = "app.identity.email")
public class IdentityEmailProperties {

    private String publicWebUrl = "http://localhost:3000";
    private long verificationTtlMinutes = 1_440;
    private long passwordResetTtlMinutes = 15;
    private long resendCooldownSeconds = 60;

    public String verificationUrl(String token) { return publicWebUrl() + "/verify-email?token=" + encode(token); }
    public String passwordResetUrl(String token) { return publicWebUrl() + "/reset-password?token=" + encode(token); }
    private String publicWebUrl() { return publicWebUrl.replaceAll("/+$", ""); }
    private String encode(String token) { return URLEncoder.encode(token, StandardCharsets.UTF_8); }

    public String getPublicWebUrl() { return publicWebUrl; }
    public void setPublicWebUrl(String publicWebUrl) { this.publicWebUrl = publicWebUrl; }
    public long getVerificationTtlMinutes() { return verificationTtlMinutes; }
    public void setVerificationTtlMinutes(long verificationTtlMinutes) { this.verificationTtlMinutes = verificationTtlMinutes; }
    public long getPasswordResetTtlMinutes() { return passwordResetTtlMinutes; }
    public void setPasswordResetTtlMinutes(long passwordResetTtlMinutes) { this.passwordResetTtlMinutes = passwordResetTtlMinutes; }
    public long getResendCooldownSeconds() { return resendCooldownSeconds; }
    public void setResendCooldownSeconds(long resendCooldownSeconds) { this.resendCooldownSeconds = resendCooldownSeconds; }
}
