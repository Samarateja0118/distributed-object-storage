package com.dfs.metadata.application.port;

/**
 * Transport-agnostic pagination request, so neither the domain nor the use-case
 * contracts depend on Spring Data's Pageable.
 */
public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 1000;
    private static final int DEFAULT_SIZE = 100;

    public PageQuery {
        page = Math.max(page, 0);
        size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }
}
