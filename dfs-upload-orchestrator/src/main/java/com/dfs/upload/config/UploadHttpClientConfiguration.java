package com.dfs.upload.config;

import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The metadata service is the one dependency still reached over HTTP; the byte
 * path moved to gRPC in Phase 2 (ADR 10), and this deliberately did not follow
 * it — see {@link UploadGrpcClientConfiguration}.
 *
 * <p>A client pinned to its base URL rather than a shared one, so the URL cannot
 * be mixed up at the call site.
 *
 * <p>Note what is deliberately absent: no credential. Authorization is attached
 * per request from the caller's own token — see
 * {@link com.dfs.upload.domain.model.CallerToken}.
 */
@Configuration
public class UploadHttpClientConfiguration {

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
