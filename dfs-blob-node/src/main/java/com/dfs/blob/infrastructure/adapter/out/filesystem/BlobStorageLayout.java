package com.dfs.blob.infrastructure.adapter.out.filesystem;

import java.nio.file.Path;

/**
 * The two directories a node writes to, resolved and created at startup.
 *
 * <p>One bean rather than two {@code Path} beans, which Spring could only tell
 * apart by parameter name.
 *
 * <p>Staging sits inside the storage root so that publishing a blob is a rename
 * within a single filesystem. Across a mount boundary the move would silently
 * degrade to a copy, and copies are not atomic.
 */
public record BlobStorageLayout(Path root, Path staging) {
}
