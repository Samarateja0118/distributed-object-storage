package com.dfs.upload.infrastructure.adapter.out;

import com.dfs.upload.domain.exception.DownstreamServiceException;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Turns a failed downstream response into a {@link DownstreamServiceException}
 * carrying enough to decide what the caller should see.
 */
public final class DownstreamErrors {

    /** Enough of a problem-detail body to be useful in a log without flooding it. */
    private static final int MAX_DETAIL_LENGTH = 512;

    private DownstreamErrors() {
    }

    public static DownstreamServiceException from(String service, ClientHttpResponse response) throws IOException {
        return new DownstreamServiceException(service, response.getStatusCode().value(), readDetail(response));
    }

    private static String readDetail(ClientHttpResponse response) {
        try (var body = response.getBody()) {
            String detail = new String(body.readAllBytes(), StandardCharsets.UTF_8).strip();
            if (detail.isEmpty()) {
                return "<empty response body>";
            }
            return detail.length() > MAX_DETAIL_LENGTH ? detail.substring(0, MAX_DETAIL_LENGTH) + "…" : detail;
        } catch (IOException e) {
            return "<unreadable response body>";
        }
    }
}
