package com.dfs.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DfsProperties.class)
public class DfsPropertiesConfiguration {
}
