package com.dfs.replication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.dfs.replication", "com.dfs.common"})
public class ReplicationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReplicationServiceApplication.class, args);
    }
}
