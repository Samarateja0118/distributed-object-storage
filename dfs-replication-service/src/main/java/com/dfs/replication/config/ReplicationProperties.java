package com.dfs.replication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dfs.replication")
public record ReplicationProperties(
        int targetReplicationFactor,
        int maxConcurrentReplications,
        String chunkRegisteredTopic,
        String blobGrpcTargetPrefix
) {
}
