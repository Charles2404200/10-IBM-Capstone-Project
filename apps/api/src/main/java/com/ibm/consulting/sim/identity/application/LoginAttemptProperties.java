package com.ibm.consulting.sim.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.identity.login-attempts")
public class LoginAttemptProperties {

    private int maxFailures = 5;
    private Duration window = Duration.ofMinutes(15);
    private long maximumTrackedAccounts = 100_000;

    public int getMaxFailures() { return maxFailures; }
    public void setMaxFailures(int maxFailures) { this.maxFailures = maxFailures; }
    public Duration getWindow() { return window; }
    public void setWindow(Duration window) { this.window = window; }
    public long getMaximumTrackedAccounts() { return maximumTrackedAccounts; }
    public void setMaximumTrackedAccounts(long maximumTrackedAccounts) {
        this.maximumTrackedAccounts = maximumTrackedAccounts;
    }
}
