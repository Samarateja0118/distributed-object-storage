package com.dfs.gc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.dfs.gc", "com.dfs.common"})
public class GcServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GcServiceApplication.class, args);
    }
}
