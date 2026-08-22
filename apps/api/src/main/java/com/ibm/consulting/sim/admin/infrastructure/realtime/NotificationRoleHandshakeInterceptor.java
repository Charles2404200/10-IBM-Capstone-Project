package com.ibm.consulting.sim.admin.infrastructure.realtime;

import com.ibm.consulting.sim.identity.domain.UserRole;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

final class NotificationRoleHandshakeInterceptor implements HandshakeInterceptor {

    static final String ROLE_ATTRIBUTE = NotificationRoleHandshakeInterceptor.class.getName() + ".role";

    private final UserRole role;

    NotificationRoleHandshakeInterceptor(UserRole role) {
        this.role = role;
    }

    //that attributes map is server-side WebSocket session state.
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        attributes.put(ROLE_ATTRIBUTE, role);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Nothing to release.
    }
}
