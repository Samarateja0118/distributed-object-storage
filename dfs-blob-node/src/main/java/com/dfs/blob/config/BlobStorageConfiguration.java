package com.dfs.blob.config;

import com.dfs.blob.infrastructure.adapter.out.filesystem.BlobStorageLayout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class BlobStorageConfiguration {

    /**
     * Created eagerly at startup: a node that cannot write to its storage root is
     * useless, and finding that out now beats failing the first upload.
     */
    @Bean
    BlobStorageLayout blobStorageLayout(BlobNodeProperties properties) throws Exception {
        Path root = Path.of(properties.storageRootPath()).toAbsolutePath().normalize();
        Path staging = root.resolve(".staging");
        Files.createDirectories(root);
        Files.createDirectories(staging);
        return new BlobStorageLayout(root, staging);
    }
}
