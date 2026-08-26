package com.ibm.consulting.sim.shared.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsAnUnmatchedEndpointToNotFoundInsteadOfInternalServerError() {
        var exception = new NoResourceFoundException(
                HttpMethod.POST,
                "/api/v1/admin/platform/publish-notifications/"
        );

        var problem = handler.handleNoResourceFound(exception);

        assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
        assertEquals(
                "https://consulting-sim.ibm.com/problems/not-found",
                problem.getType().toString()
        );
        assertEquals("The requested endpoint does not exist", problem.getDetail());
    }
}
