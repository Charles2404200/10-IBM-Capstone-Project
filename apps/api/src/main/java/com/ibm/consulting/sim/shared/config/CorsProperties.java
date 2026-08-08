package com.ibm.consulting.sim.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Single source of truth for the origins allowed to call this API (CORS) and open
 * the STOMP/WebSocket handshake ({@code /ws}) — see {@code SecurityConfig} and
 * {@code WebSocketConfig}'s {@code registerStompEndpoints}, both of which consume
 * this bean instead of hardcoding their own origin lists.
 *
 * <p>Backed by {@code app.cors.allowed-origins} (a comma-separated string in
 * {@code application.yml}, defaulting to the local Vite dev origins), which in
 * turn reads the {@code CORS_ALLOWED_ORIGINS} env var. This is the same
 * externalized-config convention used for datasource/AI/COS/cache elsewhere in
 * this codebase: deploying to a new environment (Railway, IBM Cloud Code Engine,
 * etc.) with a different frontend origin requires only an env var change, never
 * a code change or rebuild.
 */
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of("http://localhost:3000", "http://localhost:5173");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
