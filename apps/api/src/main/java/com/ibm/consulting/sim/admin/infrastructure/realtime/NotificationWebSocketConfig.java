package com.ibm.consulting.sim.admin.infrastructure.realtime;

import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.config.CorsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** Registers a distinct, role-bound STOMP endpoint for each application role. */
@Configuration
@Order(1)
public class NotificationWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketConfig.class);

    private final NotificationRoleChannelInterceptor roleInterceptor;
    private final CorsProperties corsProperties;

    public NotificationWebSocketConfig(NotificationRoleChannelInterceptor roleInterceptor,
                                       CorsProperties corsProperties) {
        this.roleInterceptor = roleInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] allowedOrigins = corsProperties.getAllowedOrigins().toArray(String[]::new);
        for (UserRole role : UserRole.values()) {
            String endpoint = NotificationWebSocketDestinations.connectionEndpoint(role);
            registry.addEndpoint(endpoint)
                    .addInterceptors(new NotificationRoleHandshakeInterceptor(role))
                    .setAllowedOriginPatterns(allowedOrigins);
            log.info("Registered role notification WebSocket endpoint: role={}, endpoint={}", role, endpoint);
        }
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Runs after the shared STOMP JWT authenticator registered at order 0.
        registration.interceptors(roleInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(
            ChannelRegistration registration) {

        registration.taskExecutor()
                .corePoolSize(16)
                .maxPoolSize(32)
                .queueCapacity(5000);
    }
}
