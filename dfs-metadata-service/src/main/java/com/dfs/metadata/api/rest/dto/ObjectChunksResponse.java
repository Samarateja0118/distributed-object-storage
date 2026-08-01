package com.dfs.metadata.api.rest.dto;

import com.dfs.metadata.domain.model.ObjectChunks;

import java.util.List;

public record ObjectChunksResponse(List<ChunkPayload> chunks) {

    public static ObjectChunksResponse from(ObjectChunks chunks) {
        return new ObjectChunksResponse(chunks.chunks().stream().map(ChunkPayload::from).toList());
    }
}
