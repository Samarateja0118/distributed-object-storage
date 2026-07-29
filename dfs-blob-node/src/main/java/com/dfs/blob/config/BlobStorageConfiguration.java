package com.dfs.blob.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class BlobStorageConfiguration {

    @Bean
    Path blobStorageRoot(BlobNodeProperties properties) throws Exception {
        Path root = Path.of(properties.storageRootPath());
        Files.createDirectories(root);
        return root;
    }
}
