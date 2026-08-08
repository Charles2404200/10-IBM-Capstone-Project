package com.ibm.consulting.sim.identity.infrastructure;

import com.ibm.consulting.sim.identity.domain.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UUID userId = jwtTokenProvider.extractUserId(token);
                var user = userRepository.findById(userId);
                if (user.isEmpty()) {
                    logAuthRejection("user_not_found", null);
                } else if (!user.get().isActive()) {
                    logAuthRejection("inactive_user", null);
                } else {
                    var activeUser = user.get();
                    MDC.put("userId", activeUser.getId().toString());
                    var auth = new UsernamePasswordAuthenticationToken(
                            activeUser, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + activeUser.getRole().name())));
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException e) {
                logAuthRejection("invalid_token", e.getClass().getSimpleName());
            }
        }
        filterChain.doFilter(request, response);
    }

    private void logAuthRejection(String reason, String errorType) {
        MDC.put("authRejectionReason", reason);
        var event = log.atWarn()
                .addKeyValue("event", "AUTHENTICATION_REJECTED");
        if (errorType != null) {
            event.addKeyValue("errorType", errorType);
        }
        event.log("Authentication rejected");
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
