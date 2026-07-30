package com.dfs.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration imported by all DFS services for shared cross-cutting behavior.
 *
 * <p>Beans that touch types from an optional starter belong in their own scanned
 * {@code @Configuration} guarded by {@code @ConditionalOnClass}, not here — see
 * {@link DfsRequestLoggingConfiguration}.
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.dfs.common")
public class DfsCommonAutoConfiguration {
}
