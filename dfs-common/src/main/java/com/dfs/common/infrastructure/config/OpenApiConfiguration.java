package com.dfs.common.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared OpenAPI metadata for public REST services.
 */
@Configuration
@ConditionalOnClass(OpenAPI.class)
public class OpenApiConfiguration {

    @Bean
    OpenAPI dfsOpenApi(
            @Value("${spring.application.name:dfs-service}") String serviceName,
            @Value("${dfs.environment:local}") String environment) {
        return new OpenAPI()
                .info(new Info()
                        .title("DFS :: " + serviceName)
                        .description("Distributed File Storage public API")
                        .version("0.1.0")
                        .contact(new Contact().name("DFS Platform").email("platform@dfs.local")));
    }
}
