package com.ibm.consulting.sim.meeting.infrastructure.realtime;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Authenticates the STOMP {@code CONNECT} frame using the same JWT the rest of the
 * API relies on ({@link JwtTokenProvider}), and attaches the resolved {@link User}
 * as the session {@link Principal} so downstream {@code @MessageMapping} handlers
 * can use {@code Principal}/{@code @AuthenticationPrincipal}-style access, and so
 * {@link MeetingSubscriptionInterceptor} can authorize subscriptions.
 *
 * <p>Browsers cannot set a custom {@code Authorization} header on the WebSocket
 * handshake HTTP request itself, so the token travels as a native STOMP header on
 * the CONNECT frame instead (sent over the already-established socket) — this is
 * the standard workaround for that browser limitation, exactly analogous to why
 * {@code usePersonaTurnStream} previously had to use {@code fetch} instead of a
 * plain {@code EventSource} for the same reason.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public StompAuthChannelInterceptor(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            if (token == null || !jwtTokenProvider.isValid(token)) {
                log.warn("WebSocket CONNECT rejected: missing or invalid token");
                throw new org.springframework.messaging.MessagingException("Invalid or missing authentication token");
            }
            UUID userId = jwtTokenProvider.extractUserId(token);
            User user = userRepository.findById(userId)
                    .filter(User::isActive)
                    .orElseThrow(() -> new org.springframework.messaging.MessagingException("Unknown or inactive user"));
            var authentication = new UsernamePasswordAuthenticationToken(
                    user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
            accessor.setUser(authentication);
        }
        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
