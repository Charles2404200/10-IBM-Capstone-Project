package com.ibm.consulting.sim.shared.email.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email.resend")
public class ResendEmailProperties {
    private String apiKey = "";
    private String baseUrl = "https://api.resend.com";
    private String from = "IBM Consulting Simulation <onboarding@resend.dev>";
    private String replyTo = "";
    private int connectTimeoutMs = 3_000;
    private int readTimeoutMs = 8_000;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public String getReplyTo() { return replyTo; }
    public void setReplyTo(String replyTo) { this.replyTo = replyTo; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
}
