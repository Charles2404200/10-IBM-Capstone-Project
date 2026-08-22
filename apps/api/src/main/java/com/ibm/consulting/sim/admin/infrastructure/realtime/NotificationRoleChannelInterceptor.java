package com.ibm.consulting.sim.admin.infrastructure.realtime;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Ensures a notification socket and its single subscription topic match the authenticated user's role. */
@Component
public class NotificationRoleChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(NotificationRoleChannelInterceptor.class);

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        UserRole endpointRole = endpointRole(accessor);
        if (StompCommand.CONNECT.equals(accessor.getCommand()) && endpointRole != null) {
            requireMatchingRole(accessor, endpointRole);
            log.debug("Authorized notification WebSocket connection: role={}", endpointRole);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor, endpointRole);
        }
        return message;
    }

    private void authorizeSubscription(StompHeaderAccessor accessor, UserRole endpointRole) {
        String destination = accessor.getDestination();
        if (endpointRole == null) {
            //endpointRole is null means it's not a notification role
            // specific topic
            if (NotificationWebSocketDestinations.isNotificationTopic(destination)) {
                throw new MessagingException("Use the role-specific notification WebSocket endpoint");
            }
            return;
        }

        requireMatchingRole(accessor, endpointRole);
        String allowedTopic = NotificationWebSocketDestinations.subscriptionTopic(endpointRole);
        if (!allowedTopic.equals(destination)) {
            log.warn("Notification subscription rejected for role {} to destination {}", endpointRole, destination);
            throw new MessagingException("Not authorized for this notification topic");
        }
        log.debug("Authorized notification subscription: role={}, destination={}", endpointRole, destination);
    }

    private void requireMatchingRole(StompHeaderAccessor accessor, UserRole endpointRole) {
        if (!(accessor.getUser() instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof User user)) {
            throw new MessagingException("Unauthenticated notification connection");
        }
        if (user.getRole() != endpointRole) {
            log.warn("User {} with role {} attempted to use the {} notification endpoint",
                    user.getId(), user.getRole(), endpointRole);
            throw new MessagingException("Not authorized for this notification endpoint");
        }
    }

    private UserRole endpointRole(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            return null;
        }
        Object role = attributes.get(NotificationRoleHandshakeInterceptor.ROLE_ATTRIBUTE);
        return role instanceof UserRole userRole ? userRole : null;
    }
}
