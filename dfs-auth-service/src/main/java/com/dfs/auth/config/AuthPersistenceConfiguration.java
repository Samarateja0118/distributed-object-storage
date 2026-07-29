package com.dfs.auth.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.dfs.auth.infrastructure.adapter.out.persistence")
@EntityScan(basePackages = "com.dfs.auth.infrastructure.adapter.out.persistence")
public class AuthPersistenceConfiguration {
}
