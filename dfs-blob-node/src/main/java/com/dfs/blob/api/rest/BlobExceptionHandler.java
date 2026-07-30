package com.dfs.blob.api.rest;

import com.dfs.blob.domain.exception.BlobNotFoundException;
import com.dfs.blob.domain.exception.BlobValidationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Translates domain failures into RFC 7807 problem details.
 *
 * <p>Ordered ahead of Spring's own handler for the same reason as the metadata
 * service: {@code spring.mvc.problemdetails.enabled} registers that one at order
 * 0, where it would outrank an unordered advice.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class BlobExceptionHandler {

    private static final String PROBLEM_BASE = "https://docs.dfs.local/problems/";

    @ExceptionHandler(BlobNotFoundException.class)
    ProblemDetail handleNotFound(BlobNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Blob not found", exception.getMessage(), "not-found");
    }

    @ExceptionHandler(BlobValidationException.class)
    ProblemDetail handleValidation(BlobValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage(), "validation-failed");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create(PROBLEM_BASE + type));
        return problemDetail;
    }
}
