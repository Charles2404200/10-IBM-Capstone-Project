package com.ibm.consulting.sim.shared.api;

import com.ibm.consulting.sim.shared.domain.DomainException;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import com.ibm.consulting.sim.admin.application.InvalidNotificationQueryException;
import com.ibm.consulting.sim.identity.domain.EmailVerificationRequiredException;
import com.ibm.consulting.sim.shared.email.application.EmailDeliveryUnavailableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Maps domain and Spring exceptions to RFC 7807 Problem Details. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_TYPE_BASE = "https://consulting-sim.ibm.com/problems/";

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", ex.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "domain-error", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> violations = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "invalid",
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "validation-error", "Request validation failed");
        pd.setProperty("violations", violations);
        return pd;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "malformed-request",
                "Request value '" + ex.getName() + "' is invalid.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail handleMissingRequestParameter(MissingServletRequestParameterException ex) {
        return problem(HttpStatus.BAD_REQUEST, "malformed-request",
                "Required request parameter '" + ex.getParameterName() + "' is missing.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleUnsupportedMethod(HttpRequestMethodNotSupportedException ex) {
        ProblemDetail body = problem(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed",
                "The requested HTTP method is not supported for this endpoint.");
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (ex.getSupportedHttpMethods() != null) {
            response.allow(ex.getSupportedHttpMethods().toArray(HttpMethod[]::new));
        }
        return response.body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableRequest(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "malformed-request",
                "Request body must be valid JSON matching the expected format.");
    }

    @ExceptionHandler(InvalidNotificationQueryException.class)
    ProblemDetail handleInvalidNotificationQuery(InvalidNotificationQueryException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-notification-query", ex.getMessage());
    }

    @ExceptionHandler(EmailVerificationRequiredException.class)
    ProblemDetail handleEmailVerificationRequired(EmailVerificationRequiredException ex) {
        return problem(HttpStatus.FORBIDDEN, "email-verification-required", ex.getMessage());
    }

    @ExceptionHandler(EmailDeliveryUnavailableException.class)
    ProblemDetail handleEmailDeliveryUnavailable(EmailDeliveryUnavailableException ex) {
        log.warn("Transactional email delivery unavailable: {}", ex.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "email-delivery-unavailable",
                "We could not send email right now. Please try again shortly.");
    }

    /** Domain guards use IllegalArgumentException for invalid authoring input. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-request", ex.getMessage());
    }

    /**
     * A persistence constraint is a correctable authoring error, not an opaque
     * server failure. Keep database implementation details out of the response.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Request violated a persistence constraint", ex);
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "data-constraint",
                "The data could not be saved because it conflicts with an existing record or constraint.");
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuth(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleForbidden(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Access denied");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResourceFound(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "The requested endpoint does not exist");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleGeneric(Exception ex) {
        // Unexpected server-side failures must never be swallowed silently —
        // without this, a 500 is undiagnosable from the API response alone.
        log.error("Unhandled exception while processing request", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "An unexpected error occurred");
    }

    private ProblemDetail problem(HttpStatus status, String type, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(PROBLEM_TYPE_BASE + type));
        return pd;
    }
}
