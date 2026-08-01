package com.dfs.upload.domain.model;

import java.io.InputStream;

/**
 * An object rejoined with its bytes: the metadata record, plus its content as a
 * stream over the chunks that make it up.
 *
 * <p>A stream rather than an array because the content is fetched chunk by chunk
 * as the caller reads it. Materialising it here would put a whole object back in
 * memory and undo what chunking is for (ADR 8). The caller owns closing it.
 */
public record ObjectDownload(RegisteredObject object, InputStream content) {
}
