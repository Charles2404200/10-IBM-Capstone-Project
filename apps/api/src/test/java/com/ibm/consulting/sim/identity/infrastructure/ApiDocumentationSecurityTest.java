package com.ibm.consulting.sim.identity.infrastructure;

import com.ibm.consulting.sim.identity.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiDocumentationSecurityTest.DocumentationEndpointStub.class)
@Import({SecurityConfig.class, ApiDocumentationSecurityTest.DocumentationEndpointStub.class})
class ApiDocumentationSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    @Test
    void anonymousUsersCannotReadOpenApiSpecification() throws Exception {
        mockMvc.perform(get("/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousUsersCannotReadSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void authenticatedUsersCanReadDocumentation() throws Exception {
        mockMvc.perform(get("/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @RestController
    static class DocumentationEndpointStub {
        @GetMapping({"/api-docs", "/swagger-ui/index.html"})
        String documentation() {
            return "documentation";
        }
    }
}
