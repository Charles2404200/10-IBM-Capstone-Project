package com.ibm.consulting.sim.shared.infrastructure.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class ObservabilityAuthenticationFilter extends OncePerRequestFilter {

    private final String observabilityToken;

    public ObservabilityAuthenticationFilter(
            @Value("${app.observability.token:}") String observabilityToken) {
        this.observabilityToken = observabilityToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().equals("/actuator/prometheus") && tokenMatches(request)) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "prometheus", null, List.of(new SimpleGrantedAuthority("ROLE_OBSERVABILITY")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private boolean tokenMatches(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(observabilityToken) || !StringUtils.hasText(authorization)
                || !authorization.startsWith("Bearer ")) {
            return false;
        }
        byte[] expected = observabilityToken.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = authorization.substring(7).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, supplied);
    }
}