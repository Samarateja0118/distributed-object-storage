package com.dfs.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.dfs.common.infrastructure.config.RedisConfiguration;

@Configuration
@Import(RedisConfiguration.class)
public class GatewayInfrastructureConfiguration {
}
