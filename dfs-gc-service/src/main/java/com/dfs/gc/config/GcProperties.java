package com.dfs.gc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "dfs.gc")
public record GcProperties(
        Duration softDeleteRetention,
        Duration orphanChunkGracePeriod,
        Duration scanInterval,
        String objectDeletedTopic
) {
}
