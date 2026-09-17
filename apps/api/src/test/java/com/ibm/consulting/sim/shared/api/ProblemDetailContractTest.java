package com.ibm.consulting.sim.shared.api;

import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import com.ibm.consulting.sim.shared.domain.DomainException;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProblemDetailContractTest.ContractController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ProblemDetailContractTest.ContractController.class)
class ProblemDetailContractTest {

    @Autowired MockMvc mockMvc;
    @MockBean FailureService failureService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    @Test
    void notFoundDomainValidationAndMalformedErrorsShareTheProblemShape() throws Exception {
        assertProblem(get("/contract/not-found"), 404, "Not Found",
                "https://consulting-sim.ibm.com/problems/not-found");
        assertProblem(get("/contract/domain"), 422, "Unprocessable Entity",
                "https://consulting-sim.ibm.com/problems/domain-error");
        mockMvc.perform(post("/contract/validated").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.violations.value").exists());
        mockMvc.perform(post("/contract/validated").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail")
                        .value("Request body must be valid JSON matching the expected format."))
                .andExpect(jsonPath("$.violations").doesNotExist());
    }

    @Test
    void persistenceFailureDoesNotExposeNestedDatabaseDetails() throws Exception {
        doThrow(new DataIntegrityViolationException(
                "duplicate row in table users constraint uk_users_email",
                new RuntimeException("SQLSTATE 23505 column password_hash")))
                .when(failureService).fail();

        mockMvc.perform(get("/contract/failure"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.detail").value(
                        "The data could not be saved because it conflicts with an existing record or constraint."))
                .andExpect(content().string(not(containsString("SQLSTATE"))))
                .andExpect(content().string(not(containsString("password_hash"))))
                .andExpect(content().string(not(containsString("uk_users_email"))));
    }

    @Test
    void unexpectedFailureDoesNotExposeSecretsPathsOrImplementationNames() throws Exception {
        doThrow(new RuntimeException(
                "token=top-secret /srv/private com.ibm.consulting.sim.persistence.InternalRepository"))
                .when(failureService).fail();

        mockMvc.perform(get("/contract/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("top-secret"))))
                .andExpect(content().string(not(containsString("/srv/private"))))
                .andExpect(content().string(not(containsString("com.ibm"))))
                .andExpect(content().string(not(containsString("RuntimeException"))));
    }

    private void assertProblem(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            int expectedStatus,
            String expectedTitle,
            String expectedType) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.title").value(expectedTitle))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.type").value(expectedType))
                .andExpect(jsonPath("$.violations").doesNotExist());
    }

    interface FailureService {
        void fail();
    }

    @RestController
    @RequestMapping("/contract")
    static class ContractController {
        private final FailureService failureService;

        ContractController(FailureService failureService) {
            this.failureService = failureService;
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("Widget", "missing");
        }

        @GetMapping("/domain")
        void domain() {
            throw new ContractDomainException("The request conflicts with the current domain state");
        }

        @PostMapping("/validated")
        void validated(@Valid @RequestBody ContractRequest request) {
            // Request parsing and validation are the contract under test.
        }

        @GetMapping("/failure")
        void failure() {
            failureService.fail();
        }
    }

    record ContractRequest(@NotBlank String value) {}

    static final class ContractDomainException extends DomainException {
        ContractDomainException(String message) {
            super(message);
        }
    }
}
