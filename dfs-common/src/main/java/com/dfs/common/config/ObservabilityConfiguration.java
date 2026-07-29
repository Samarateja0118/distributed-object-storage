package com.dfs.common.config;

import org.springframework.boot.actuate.autoconfigure.tracing.TracingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

/**
 * Placeholder for OpenTelemetry/Micrometer tracing wiring.
 * Full exporter configuration lives in each service's application.yml.
 */
@Configuration
@ConditionalOnClass(TracingProperties.class)
public class ObservabilityConfiguration {
}
