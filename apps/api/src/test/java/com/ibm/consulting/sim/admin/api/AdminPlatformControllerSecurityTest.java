package com.ibm.consulting.sim.admin.api;

import com.ibm.consulting.sim.admin.application.AdminNotificationService;
import com.ibm.consulting.sim.admin.application.NotificationQueryService;
import com.ibm.consulting.sim.admin.application.NotificationReadStatusPage;
import com.ibm.consulting.sim.admin.application.PlatformOverviewService;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.identity.infrastructure.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPlatformController.class)
@Import(SecurityConfig.class)
class AdminPlatformControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlatformOverviewService overviewService;

    @MockBean
    private AdminNotificationService adminNotificationService;

    @MockBean
    private NotificationQueryService notificationQueryService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "LEARNER")
    void learnerCannotRequestCriticalDelivery() throws Exception {
        mockMvc.perform(post("/api/v1/admin/platform/publish-notifications")
                        .contentType("application/json")
                        .content("""
                                {
                                  "topicName": "Urgent",
                                  "message": "Reset your password",
                                  "roles": ["LEARNER"],
                                  "priority": "CRITICAL"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void arbitraryPriorityValueIsRejectedByJackson() throws Exception {
        mockMvc.perform(post("/api/v1/admin/platform/publish-notifications")
                        .contentType("application/json")
                        .content("""
                                {
                                  "topicName": "Urgent",
                                  "message": "Message",
                                  "roles": ["LEARNER"],
                                  "priority": "999999"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void infrastructureOnlyPriorityIsRejectedByAdminApi() throws Exception {
        mockMvc.perform(post("/api/v1/admin/platform/publish-notifications")
                        .contentType("application/json")
                        .content("""
                                {
                                  "topicName": "New course",
                                  "message": "A new course is available",
                                  "roles": ["LEARNER"],
                                  "priority": "HIGH"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "LEARNER")
    void learnerCannotEnumerateNotificationRecipients() throws Exception {
        mockMvc.perform(get("/api/v1/admin/platform/notifications/{eventId}/read-status/users",
                        UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void administratorCanRequestABoundedRecipientPage() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(notificationQueryService.getReadStatusUsers(eventId, 50, null))
                .thenReturn(new NotificationReadStatusPage(List.of(), null, false));

        mockMvc.perform(get("/api/v1/admin/platform/notifications/{eventId}/read-status/users",
                        eventId))
                .andExpect(status().isOk());
    }
}
