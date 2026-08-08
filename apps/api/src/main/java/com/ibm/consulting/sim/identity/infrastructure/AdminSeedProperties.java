package com.ibm.consulting.sim.identity.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bootstrap configuration for the one-time administrator seed account (§11 RBAC).
 * All values are sourced from environment variables (see {@code .env.example}):
 * {@code ADMIN_EMAIL}, {@code ADMIN_PASSWORD}, {@code ADMIN_DISPLAY_NAME}. Seeding is
 * skipped entirely when email/password are not supplied, so this has zero effect on
 * environments (e.g. CI, tests) that don't configure it.
 */
@ConfigurationProperties(prefix = "app.admin.seed")
public class AdminSeedProperties {

    private String email;
    private String password;
    private String displayName = "Platform Administrator";

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
