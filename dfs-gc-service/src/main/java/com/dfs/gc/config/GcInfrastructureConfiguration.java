package com.dfs.gc.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.dfs.common.infrastructure.config.DatabaseConfiguration;
import com.dfs.common.infrastructure.config.KafkaConfiguration;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(GcProperties.class)
@EnableJpaRepositories(basePackages = "com.dfs.gc.infrastructure.adapter.out.persistence")
@EntityScan(basePackages = "com.dfs.gc.infrastructure.adapter.out.persistence")
@Import({DatabaseConfiguration.class, KafkaConfiguration.class})
public class GcInfrastructureConfiguration {
}
