package com.ibm.consulting.sim.identity.api;

import com.ibm.consulting.sim.identity.application.AdminUserService;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminUserMalformedRequestContractTest {

    @Autowired MockMvc mockMvc;
    @MockBean AdminUserService adminUserService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    @Test
    void unknownRoleAndInvalidUserIdentifierReturnSafeBadRequests() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"admin@example.com","password":"StrongPass123!",
                         "displayName":"Admin User","role":"ROOT","skipEmailVerification":false}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("https://consulting-sim.ibm.com/problems/malformed-request"));
        mockMvc.perform(patch("/api/v1/admin/users/not-a-uuid/role")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"LEARNER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(adminUserService);
    }

    @Test
    void invalidDirectoryQueryParametersAreRejectedBeforeServiceInvocation() throws Exception {
        for (String query : java.util.List.of(
                "?page=-1", "?page=not-a-number", "?size=0", "?size=101", "?role=ROOT")) {
            mockMvc.perform(get("/api/v1/admin/users" + query))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400));
        }

        verifyNoInteractions(adminUserService);
    }
}
