package com.dfs.upload.domain.exception;

public class ObjectTooLargeException extends RuntimeException {

    public ObjectTooLargeException(long limitBytes) {
        super("Object exceeds the maximum upload size of %d bytes".formatted(limitBytes));
    }
}
