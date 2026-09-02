package com.ibm.consulting.sim.admin.api;

import com.ibm.consulting.sim.admin.application.InvalidNotificationQueryException;
import com.ibm.consulting.sim.admin.application.NotificationPageResponse;
import com.ibm.consulting.sim.admin.application.NotificationQueryService;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.identity.infrastructure.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationQueryService service;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    @Test
    void authenticationIsRequired() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void identityAndRoleComeOnlyFromAuthenticatedPrincipal() throws Exception {
        User learner = User.create("learner@example.com", "hash", "Learner", UserRole.LEARNER);
        when(service.pageForUser(learner.getId(), UserRole.LEARNER, 30, null, null))
                .thenReturn(new NotificationPageResponse(List.of(), null, false));

        mockMvc.perform(get("/api/v1/notifications")
                        .with(authentication(authenticationFor(learner))))
                .andExpect(status().isOk());

        verify(service).pageForUser(learner.getId(), UserRole.LEARNER, 30, null, null);
    }

    @Test
    void injectionStyleFieldSelectionReturnsBadRequest() throws Exception {
        User learner = User.create("learner2@example.com", "hash", "Learner", UserRole.LEARNER);
        String fields = "eventId,(SELECT password FROM users)";
        when(service.pageForUser(learner.getId(), UserRole.LEARNER, 30, null, fields))
                .thenThrow(new InvalidNotificationQueryException("Unsupported notification field"));

        mockMvc.perform(get("/api/v1/notifications")
                        .queryParam("fields", fields)
                        .with(authentication(authenticationFor(learner))))
                .andExpect(status().isBadRequest());
    }

    private UsernamePasswordAuthenticationToken authenticationFor(User user) {
        return new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }
}

