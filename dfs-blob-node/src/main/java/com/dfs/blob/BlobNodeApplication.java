package com.dfs.blob;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.dfs.blob", "com.dfs.common"})
public class BlobNodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlobNodeApplication.class, args);
    }
}
