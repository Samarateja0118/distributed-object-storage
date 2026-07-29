package com.dfs.blob.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC server for internal chunk read/write/delete operations.
 */
@Configuration
@EnableConfigurationProperties(BlobNodeProperties.class)
public class BlobGrpcConfiguration {
}
