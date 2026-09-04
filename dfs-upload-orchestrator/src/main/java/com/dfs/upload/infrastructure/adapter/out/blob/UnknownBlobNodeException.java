package com.dfs.upload.infrastructure.adapter.out.blob;

import com.dfs.common.exception.DfsException;

import java.util.Collection;

/**
 * A chunk names a node this orchestrator has no configuration for.
 *
 * <p>Its own exception rather than a generic failure because the cause is
 * specific and the fix is specific: an id was dropped from {@code
 * dfs.upload.blob-nodes} while chunks were still recorded against it. The bytes
 * are almost certainly fine.
 */
public class UnknownBlobNodeException extends DfsException {

    public UnknownBlobNodeException(String nodeId, Collection<String> configured) {
        super("No blob node is configured with id \"%s\"; known ids are %s".formatted(nodeId, configured));
    }
}
