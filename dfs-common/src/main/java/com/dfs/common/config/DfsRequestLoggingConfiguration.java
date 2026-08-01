package com.dfs.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/**
 * Shared request logging for the services that serve HTTP.
 *
 * <p>This lives apart from {@link DfsCommonAutoConfiguration} because a {@code @Bean}
 * method's return type is loaded when the declaring class is introspected, before any
 * method-level condition is evaluated. Declared there, the filter's presence in the
 * signature broke context refresh for every service without spring-web on the classpath
 * — the guard has to keep the class from being introspected at all, not keep the bean
 * from being created.
 */
@Configuration
@ConditionalOnClass(CommonsRequestLoggingFilter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DfsRequestLoggingConfiguration {

    @Bean
    CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeClientInfo(true);
        filter.setIncludeQueryString(true);
        filter.setIncludeHeaders(false);
        filter.setIncludePayload(false);
        filter.setMaxPayloadLength(0);
        return filter;
    }
}
