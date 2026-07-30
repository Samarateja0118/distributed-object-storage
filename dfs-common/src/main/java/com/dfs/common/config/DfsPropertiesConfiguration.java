package com.dfs.common.config;

import com.dfs.common.security.DfsJwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({DfsProperties.class, DfsJwtProperties.class})
public class DfsPropertiesConfiguration {
}
