package com.anbit.archetype.common.error;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Centralised exception handling. Every error leaves the service as an RFC 9457
 * {@link ProblemDetail} with a stable {@code type} URN, a human {@code title}, and a
 * {@code timestamp}.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} means Spring's own exceptions
 * (unreadable JSON, type mismatch, unsupported method/media type, missing params, method
 * validation, unknown route…) are already mapped to {@code ProblemDetail}; we enrich them
 * with a timestamp and add handlers for our domain and persistence exceptions.
 *
 * <p>Add a new handler here rather than catching-and-formatting inside controllers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- Domain / application exceptions ----

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", "resource-not-found", ex.getMessage());
    }

    // ---- Validation on path/query params or at the service layer ----

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "validation",
                "One or more request values are invalid");
        problem.setProperty("errors", ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList());
        return problem;
    }

    // ---- Persistence / concurrency ----

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        // Don't echo the SQL/constraint detail to the client; it's logged server-side.
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Data integrity violation", "data-integrity",
                "The request conflicts with the current state of the resource");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification", "optimistic-lock",
                "The resource was modified concurrently; reload and retry");
    }

    // ---- Fallback: anything unmapped is a 500, logged with full stack trace ----

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "internal", null);
    }

    // ---- Enrich Spring's built-in @RequestBody validation with the field errors ----

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ex.getBody();
        problem.setTitle("Validation failed");
        problem.setType(type("validation"));
        problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList());
        return super.handleExceptionInternal(ex, problem, headers, status, request);
    }

    // ---- Add a timestamp to every ProblemDetail produced by the base class handlers ----

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            Map<String, Object> props = problem.getProperties();
            if (props == null || !props.containsKey("timestamp")) {
                problem.setProperty("timestamp", Instant.now());
            }
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String typeSlug, @Nullable String detail) {
        ProblemDetail problem = (detail != null)
                ? ProblemDetail.forStatusAndDetail(status, detail)
                : ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setType(type(typeSlug));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    private static URI type(String slug) {
        return URI.create("urn:problem:" + slug);
    }
}
