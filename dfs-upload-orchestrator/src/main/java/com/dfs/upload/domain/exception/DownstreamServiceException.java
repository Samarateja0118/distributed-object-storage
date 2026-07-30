package com.dfs.upload.domain.exception;

/**
 * A downstream service answered, but not with success.
 *
 * <p>Carries which service failed and with what status, because the two are
 * handled differently: a rejected token has to reach the caller as 401/403 so
 * they know to re-authenticate, while anything else is the orchestrator's
 * problem to report as a bad gateway rather than blame on the request.
 */
public class DownstreamServiceException extends RuntimeException {

    private final String service;
    private final int statusCode;

    public DownstreamServiceException(String service, int statusCode, String detail) {
        super("%s responded %d: %s".formatted(service, statusCode, detail));
        this.service = service;
        this.statusCode = statusCode;
    }

    public String service() {
        return service;
    }

    public int statusCode() {
        return statusCode;
    }
}
