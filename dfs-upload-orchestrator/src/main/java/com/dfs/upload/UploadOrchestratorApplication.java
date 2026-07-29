package com.dfs.upload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.dfs.upload", "com.dfs.common"})
public class UploadOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(UploadOrchestratorApplication.class, args);
    }
}
