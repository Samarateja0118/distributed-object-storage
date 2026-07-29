package com.dfs.upload.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.dfs.common.infrastructure.config.KafkaConfiguration;
import com.dfs.common.infrastructure.config.OpenApiConfiguration;
import com.dfs.common.infrastructure.config.RedisConfiguration;

@Configuration
@EnableConfigurationProperties(UploadProperties.class)
@Import({RedisConfiguration.class, KafkaConfiguration.class, OpenApiConfiguration.class})
public class UploadInfrastructureConfiguration {
}
