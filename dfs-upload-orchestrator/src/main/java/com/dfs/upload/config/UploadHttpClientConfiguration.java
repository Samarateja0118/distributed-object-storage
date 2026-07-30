package com.dfs.upload.config;

import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * One client per downstream service, each pinned to its base URL.
 *
 * <p>Separate beans rather than one shared client so a base URL can never be
 * mixed up at the call site, and so the two can diverge later (timeouts,
 * retries) without either inheriting the other's settings.
 *
 * <p>Note what is deliberately absent: neither client carries a credential.
 * Authorization is attached per request from the caller's own token — see
 * {@link com.dfs.upload.domain.model.CallerToken}.
 */
@Configuration
public class UploadHttpClientConfiguration {

    @Bean
    RestClient blobClient(RestClient.Builder builder, UploadProperties properties) {
        return builder.clone()
                .baseUrl(properties.blobBaseUrl())
                .requestFactory(requestFactory(properties))
                .build();
    }

    @Bean
    RestClient metadataClient(RestClient.Builder builder, UploadProperties properties) {
        return builder.clone()
                .baseUrl(properties.metadataBaseUrl())
                .requestFactory(requestFactory(properties))
                .build();
    }

    private static ClientHttpRequestFactory requestFactory(UploadProperties properties) {
        return ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(properties.downstreamTimeout())
                .withReadTimeout(properties.downstreamTimeout()));
    }
}
