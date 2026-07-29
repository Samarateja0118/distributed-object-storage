package com.dfs.metadata.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.dfs.common.infrastructure.config.DatabaseConfiguration;
import com.dfs.common.infrastructure.config.KafkaConfiguration;
import com.dfs.common.infrastructure.config.OpenApiConfiguration;
import com.dfs.common.infrastructure.config.RedisConfiguration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(MetadataProperties.class)
@EnableJpaRepositories(basePackages = "com.dfs.metadata.infrastructure.adapter.out.persistence")
@EntityScan(basePackages = "com.dfs.metadata.infrastructure.adapter.out.persistence")
@Import({DatabaseConfiguration.class, RedisConfiguration.class, KafkaConfiguration.class, OpenApiConfiguration.class})
public class MetadataInfrastructureConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
