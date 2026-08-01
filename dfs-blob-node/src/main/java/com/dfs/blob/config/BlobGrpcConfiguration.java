package com.dfs.blob.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC surface for internal chunk transfer. Reads and writes only — reclaiming a
 * chunk is GC's work, because a deduplicated chunk has no owner to authorize a
 * delete against (ADR 4).
 *
 * <p>The server itself is started by the gRPC starter from {@code grpc.server.*},
 * and calls are authenticated by the shared interceptor from {@code dfs-common};
 * this holds the node properties the services depend on.
 */
@Configuration
@EnableConfigurationProperties(BlobNodeProperties.class)
public class BlobGrpcConfiguration {
}
