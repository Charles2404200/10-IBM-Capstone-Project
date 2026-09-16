package com.ibm.consulting.sim.outreach.api;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.outreach.application.CapabilityBriefService;
import com.ibm.consulting.sim.outreach.application.OutreachService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OutreachController.class)
@AutoConfigureMockMvc(addFilters = false)
class OutreachControllerValidationTest {

    @Autowired MockMvc mockMvc;
    @MockBean OutreachService outreachService;
    @MockBean CapabilityBriefService capabilityBriefService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    private final UUID engagementId = UUID.randomUUID();
    private final User learner = User.create(
            "outreach@example.com", "hash", "Outreach Learner", UserRole.LEARNER);

    @Test
    void requestIdIsForwardedToTheIdempotentServiceContract() throws Exception {
        send("request-123").andExpect(status().isCreated());

        verify(outreachService).send(
                engagementId, learner.getId(), "Valid subject", "Valid outreach body", "request-123");
    }

    @Test
    void omittedRequestIdRemainsBackwardCompatible() throws Exception {
        mockMvc.perform(post("/api/v1/engagements/{id}/outreach", engagementId)
                        .with(learnerAuthentication())
                        .contentType("application/json")
                        .content("""
                                {"subject":"Valid subject","body":"Valid outreach body"}
                                """))
                .andExpect(status().isCreated());

        verify(outreachService).send(
                engagementId, learner.getId(), "Valid subject", "Valid outreach body", null);
    }

    @Test
    void oversizedRequestIdIsRejectedBeforeApplicationLogic() throws Exception {
        send("r".repeat(101)).andExpect(status().isBadRequest());

        verifyNoInteractions(outreachService);
    }

    private org.springframework.test.web.servlet.ResultActions send(String requestId) throws Exception {
        return mockMvc.perform(post("/api/v1/engagements/{id}/outreach", engagementId)
                .with(learnerAuthentication())
                .contentType("application/json")
                .content("""
                        {"subject":"Valid subject","body":"Valid outreach body","requestId":"%s"}
                        """.formatted(requestId)));
    }

    private RequestPostProcessor learnerAuthentication() {
        return request -> {
            Authentication authentication =
                    new UsernamePasswordAuthenticationToken(learner, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setUserPrincipal(authentication);
            return request;
        };
    }
}
