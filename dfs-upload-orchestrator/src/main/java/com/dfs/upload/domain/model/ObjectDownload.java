package com.dfs.upload.domain.model;

/**
 * An object rejoined with its bytes: the metadata record plus the content
 * fetched from the blob node.
 */
public record ObjectDownload(RegisteredObject object, byte[] content) {
}
