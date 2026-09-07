package com.ibm.consulting.sim.shared.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void returnsOneViolationPerFieldWhenMultipleConstraintsFail() {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "displayName", "must not be blank"));
        bindingResult.addError(new FieldError("request", "displayName", "size must be between 2 and 80"));
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        var problem = handler.handleValidation(exception);

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals(Map.of("displayName", "must not be blank"), problem.getProperties().get("violations"));
    }

    @Test
    void mapsMalformedTypedRequestValuesToBadRequest() {
        var exception = new MethodArgumentTypeMismatchException(
                "not-a-uuid", UUID.class, "eventId", null, new IllegalArgumentException());

        var problem = handler.handleTypeMismatch(exception);

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals(
                "https://consulting-sim.ibm.com/problems/malformed-request",
                problem.getType().toString());
        assertEquals("Request value 'eventId' is invalid.", problem.getDetail());
    }

    @Test
    void mapsMissingRequiredRequestParametersToBadRequest() {
        var exception = new MissingServletRequestParameterException("engagementIdA", "UUID");

        var problem = handler.handleMissingRequestParameter(exception);

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals("Required request parameter 'engagementIdA' is missing.", problem.getDetail());
    }

    @Test
    void mapsUnsupportedHttpMethodsToMethodNotAllowed() {
        var response = handler.handleUnsupportedMethod(
                new HttpRequestMethodNotSupportedException("DELETE"));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals(
                "https://consulting-sim.ibm.com/problems/method-not-allowed",
                response.getBody().getType().toString());
    }
}
