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
import com.dfs.metadata.application.service.MultipartPolicy;

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

    /**
     * Built at startup, so a missing or nonsensical multipart block stops the
     * service rather than surfacing on the first upload.
     */
    @Bean
    MultipartPolicy multipartPolicy(MetadataProperties properties) {
        MetadataProperties.Multipart multipart = properties.multipart();
        if (multipart == null || multipart.minPartSize() == null) {
            throw new IllegalStateException("dfs.metadata.multipart is not configured");
        }
        return new MultipartPolicy(
                multipart.sessionTtl(),
                multipart.minPartSize().toBytes(),
                multipart.sweepBatchSize());
    }
}
