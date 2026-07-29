package com.dfs.replication.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.dfs.common.infrastructure.config.DatabaseConfiguration;
import com.dfs.common.infrastructure.config.KafkaConfiguration;

@Configuration
@EnableConfigurationProperties(ReplicationProperties.class)
@EnableJpaRepositories(basePackages = "com.dfs.replication.infrastructure.adapter.out.persistence")
@EntityScan(basePackages = "com.dfs.replication.infrastructure.adapter.out.persistence")
@Import({DatabaseConfiguration.class, KafkaConfiguration.class})
public class ReplicationInfrastructureConfiguration {
}
