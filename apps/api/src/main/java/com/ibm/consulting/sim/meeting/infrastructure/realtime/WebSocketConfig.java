package com.ibm.consulting.sim.meeting.infrastructure.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Real-time transport for the live meeting chat (see
 * docs/architecture/LIVE_MEETING_REALTIME.md): STOMP over a native WebSocket at
 * {@code /ws}, replacing the previous per-message HTTP POST + Server-Sent-Events
 * round trip with a single persistent connection per meeting session.
 *
 * <p>Deliberately scoped to the {@code meeting} module rather than {@code shared}:
 * this is currently the only feature needing bidirectional real-time messaging. If
 * a second feature needs the same transport later, extract this + the auth/ownership
 * interceptors into {@code shared.config} at that point — premature generalisation
 * here would just be unused surface area today.
 *
 * <p>A plain browser {@code WebSocket} cannot attach a custom {@code Authorization}
 * header to its handshake request (the same limitation {@code EventSource} has, see
 * {@code usePersonaTurnStream}'s predecessor), so authentication happens one layer
 * up, at the STOMP protocol level: {@link StompAuthChannelInterceptor} validates the
 * JWT carried as a STOMP {@code CONNECT} frame header (browsers *can* set arbitrary
 * STOMP frame headers, since they travel over the already-established WebSocket
 * connection, not the HTTP upgrade request), and {@link MeetingSubscriptionInterceptor}
 * authorizes each {@code SUBSCRIBE} against the requesting user's ownership of the
 * target meeting.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authInterceptor;
    private final MeetingSubscriptionInterceptor subscriptionInterceptor;

    public WebSocketConfig(StompAuthChannelInterceptor authInterceptor,
                            MeetingSubscriptionInterceptor subscriptionInterceptor) {
        this.authInterceptor = authInterceptor;
        this.subscriptionInterceptor = subscriptionInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:3000", "http://localhost:5173");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Simple in-memory broker: sufficient for a single API instance. If the
        // platform is later scaled horizontally, swap this for a STOMP relay to an
        // external broker (e.g. RabbitMQ) — no controller/frontend changes needed,
        // mirroring the swap-the-adapter-not-the-call-site convention already used
        // for CacheConfig/ObjectStorageClient elsewhere in this codebase.
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Order matters: authentication (CONNECT) must run before authorization
        // (SUBSCRIBE), since the subscription check reads the Principal the auth
        // interceptor attaches to the STOMP session.
        registration.interceptors(authInterceptor, subscriptionInterceptor);
    }
}
