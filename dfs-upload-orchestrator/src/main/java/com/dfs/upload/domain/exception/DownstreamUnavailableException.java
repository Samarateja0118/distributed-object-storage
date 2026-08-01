package com.dfs.upload.domain.exception;

/**
 * A downstream service never answered — nothing was listening, or the deadline
 * passed before a response arrived.
 *
 * <p>Distinct from {@link DownstreamServiceException}, which means the service
 * answered and refused. The difference is worth keeping because it is the
 * difference between a dependency that is down and one that is working
 * correctly and saying no, and only the first is retryable as-is.
 *
 * <p>The REST clients signal this with Spring's {@code ResourceAccessException};
 * gRPC has no equivalent to catch, so the blob adapter raises this instead and
 * both reach the caller as the same status.
 */
public class DownstreamUnavailableException extends RuntimeException {

    public DownstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
