package com.dfs.upload.api.rest;

import com.dfs.upload.domain.exception.BlobNodeNotConfiguredException;
import com.dfs.upload.domain.exception.DownstreamServiceException;
import com.dfs.upload.domain.exception.DownstreamUnavailableException;
import com.dfs.upload.domain.exception.ObjectNotFoundException;
import com.dfs.upload.domain.exception.ObjectTooLargeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import java.net.URI;

/**
 * Translates orchestration failures into RFC 7807 problem details.
 *
 * <p>Ordered ahead of Spring's own handler for the same reason as the other
 * services: {@code spring.mvc.problemdetails.enabled} registers that one at
 * order 0, where it would outrank an unordered advice.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class UploadExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(UploadExceptionHandler.class);

    private static final String PROBLEM_BASE = "https://docs.dfs.local/problems/";

    @ExceptionHandler(ObjectNotFoundException.class)
    ProblemDetail handleNotFound(ObjectNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Object not found", exception.getMessage(), "not-found");
    }

    @ExceptionHandler(ObjectTooLargeException.class)
    ProblemDetail handleTooLarge(ObjectTooLargeException exception) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Object too large", exception.getMessage(), "object-too-large");
    }

    /**
     * A downstream refusal of the caller's token is the caller's to fix, so
     * 401 and 403 pass through unchanged. Everything else is a dependency
     * failing, which is this service's problem to own as a 502 — reporting it as
     * anything in the 4xx range would blame a request that was fine.
     */
    @ExceptionHandler(DownstreamServiceException.class)
    ProblemDetail handleDownstream(DownstreamServiceException exception) {
        HttpStatus status = switch (exception.statusCode()) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_GATEWAY;
        };

        if (status == HttpStatus.BAD_GATEWAY) {
            log.error("Downstream call failed", exception);
            return problem(status, "Upstream service failed",
                    "The %s could not complete this request".formatted(exception.service()),
                    "downstream-failure");
        }
        return problem(status, "Not authorized",
                "The %s rejected the caller's credentials".formatted(exception.service()), "not-authorized");
    }

    /**
     * The downstream service never answered at all — connection refused, or the
     * timeout elapsed. Two exception types for one condition, because the two
     * transports report it differently: the REST client raises Spring's, and
     * gRPC has no equivalent to catch, so the blob adapter raises the other.
     */
    @ExceptionHandler({ResourceAccessException.class, DownstreamUnavailableException.class})
    ProblemDetail handleUnreachable(Exception exception) {
        log.error("Downstream service unreachable", exception);
        return problem(HttpStatus.GATEWAY_TIMEOUT, "Upstream service unreachable",
                "A storage service did not respond in time", "downstream-unreachable");
    }

    /**
     * A chunk is recorded on a node this deployment has no address for. Neither
     * a missing object (the bytes may well exist) nor a failing dependency
     * (nothing was called): this service is misconfigured, so it says so as a
     * 500 and logs the node id an operator needs. The id stays out of the
     * response body, which a client has no use for.
     */
    @ExceptionHandler(BlobNodeNotConfiguredException.class)
    ProblemDetail handleNodeNotConfigured(BlobNodeNotConfiguredException exception) {
        log.error("Chunk recorded on unconfigured blob node {}", exception.nodeId(), exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Storage node not configured",
                "Part of this object is stored on a node this service is not configured to reach",
                "node-not-configured");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create(PROBLEM_BASE + type));
        return problemDetail;
    }
}
