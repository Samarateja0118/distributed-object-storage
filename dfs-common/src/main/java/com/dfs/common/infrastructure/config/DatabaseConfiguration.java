package com.dfs.common.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Enables JDBC transaction management when a datasource is present.
 *
 * <p>Guarded on {@code JdbcTemplate} rather than on {@code javax.sql.DataSource}:
 * the latter ships with the JDK, so it is present in every service and conditions
 * on it always match. Services without the JDBC starter — the blob node, for one —
 * would then register {@link DataSourceProperties} and fail at startup on a
 * missing {@code spring-tx}.
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@EnableConfigurationProperties(DataSourceProperties.class)
@EnableTransactionManagement
public class DatabaseConfiguration {
}
