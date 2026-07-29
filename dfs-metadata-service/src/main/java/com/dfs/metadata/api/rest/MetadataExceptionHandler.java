package com.dfs.metadata.api.rest;

import com.dfs.metadata.domain.exception.BucketAlreadyExistsException;
import com.dfs.metadata.domain.exception.BucketNotEmptyException;
import com.dfs.metadata.domain.exception.BucketNotFoundException;
import com.dfs.metadata.domain.exception.MetadataValidationException;
import com.dfs.metadata.domain.exception.ObjectNotFoundException;
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
 * Translates domain and persistence failures into RFC 7807 problem details.
 *
 * <p>Ordered ahead of Spring's own {@code ProblemDetailsExceptionHandler}, which
 * {@code spring.mvc.problemdetails.enabled} registers at order 0. Without this,
 * that handler would claim {@link MethodArgumentNotValidException} first and the
 * per-field {@code errors} map below would never reach the client.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class MetadataExceptionHandler {

    private static final String PROBLEM_BASE = "https://docs.dfs.local/problems/";

    @ExceptionHandler({BucketNotFoundException.class, ObjectNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage(), "not-found");
    }

    @ExceptionHandler(BucketAlreadyExistsException.class)
    ProblemDetail handleBucketExists(BucketAlreadyExistsException exception) {
        return problem(HttpStatus.CONFLICT, "Bucket already exists", exception.getMessage(), "bucket-already-exists");
    }

    @ExceptionHandler(BucketNotEmptyException.class)
    ProblemDetail handleBucketNotEmpty(BucketNotEmptyException exception) {
        return problem(HttpStatus.CONFLICT, "Bucket not empty", exception.getMessage(), "bucket-not-empty");
    }

    @ExceptionHandler(MetadataValidationException.class)
    ProblemDetail handleDomainValidation(MetadataValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage(), "validation-failed");
    }

    /**
     * The unique indexes are the real guard against concurrent creates, so a
     * constraint violation is reported as a conflict rather than a server error.
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
