package com.dfs.metadata.api.rest.dto;

import com.dfs.metadata.application.port.PageResult;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <D, R> PageResponse<R> from(PageResult<D> result, Function<D, R> mapper) {
        return new PageResponse<>(
                result.items().stream().map(mapper).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }
}
