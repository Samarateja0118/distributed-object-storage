package com.dfs.auth.api.rest;

import com.dfs.auth.domain.exception.ApiKeyNotFoundException;
import com.dfs.auth.domain.exception.AuthValidationException;
import com.dfs.auth.domain.exception.InvalidCredentialsException;
import com.dfs.auth.domain.exception.TenantAlreadyExistsException;
import com.dfs.auth.domain.exception.TenantNotFoundException;
import com.dfs.auth.domain.exception.UserAlreadyExistsException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates domain failures into RFC 7807 problem details.
 *
 * <p>Ordered ahead of Spring's own {@code ProblemDetailsExceptionHandler}, which
 * {@code spring.mvc.problemdetails.enabled} registers at order 0 and which would
 * otherwise claim {@link MethodArgumentNotValidException} and drop the per-field
 * {@code errors} map.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class AuthExceptionHandler {

    private static final String PROBLEM_BASE = "https://docs.dfs.local/problems/";

    /**
     * Deliberately uniform: the caller learns that authentication failed and
     * nothing about which part was wrong.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication failed", exception.getMessage(),
                "invalid-credentials");
    }

    @ExceptionHandler({TenantNotFoundException.class, ApiKeyNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage(), "not-found");
    }

    @ExceptionHandler({TenantAlreadyExistsException.class, UserAlreadyExistsException.class})
    ProblemDetail handleAlreadyExists(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Resource already exists", exception.getMessage(), "already-exists");
    }

    @ExceptionHandler(AuthValidationException.class)
    ProblemDetail handleDomainValidation(AuthValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage(), "validation-failed");
    }

    /**
     * The unique indexes are the real guard against concurrent registration, so a
     * constraint violation is a conflict rather than a server error.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConstraintViolation(DataIntegrityViolationException exception) {
        return problem(HttpStatus.CONFLICT, "Conflicting concurrent modification",
                "The request conflicts with the current state of the resource", "conflict");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBeanValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        exception.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "One or more fields failed validation", "validation-failed");
        detail.setProperty("errors", errors);
        return detail;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create(PROBLEM_BASE + type));
        return problemDetail;
    }
}
