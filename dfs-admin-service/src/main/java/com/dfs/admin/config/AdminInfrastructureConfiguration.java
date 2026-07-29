package com.dfs.admin.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.dfs.common.infrastructure.config.DatabaseConfiguration;
import com.dfs.common.infrastructure.config.OpenApiConfiguration;

@Configuration
@EnableConfigurationProperties(AdminProperties.class)
@EnableJpaRepositories(basePackages = "com.dfs.admin.infrastructure.adapter.out.persistence")
@EntityScan(basePackages = "com.dfs.admin.infrastructure.adapter.out.persistence")
@Import({DatabaseConfiguration.class, OpenApiConfiguration.class})
public class AdminInfrastructureConfiguration {
}
