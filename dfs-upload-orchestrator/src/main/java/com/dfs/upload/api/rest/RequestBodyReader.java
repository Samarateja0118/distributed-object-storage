package com.dfs.upload.api.rest;

import com.dfs.upload.domain.exception.ObjectTooLargeException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads a request body into memory, refusing to grow past a limit.
 *
 * <p>Phase 1 has to hold the whole object: the blob node is content-addressed,
 * so the digest has to be known before the first byte can be sent, and that
 * means reading the content to the end first. The limit is what keeps that
 * defensible — {@code Content-Length} is not, since a client can understate it
 * or omit it under chunked encoding, so the cap is enforced against the bytes
 * actually read.
 *
 * <p>Phase 2's chunked upload path removes the need to buffer at all.
 */
final class RequestBodyReader {

    private static final int CHUNK_SIZE = 8192;

    private RequestBodyReader() {
    }

    static byte[] readAtMost(InputStream body, long limitBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[CHUNK_SIZE];
        long total = 0;

        int read;
        while ((read = body.read(chunk)) != -1) {
            total += read;
            if (total > limitBytes) {
                throw new ObjectTooLargeException(limitBytes);
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
