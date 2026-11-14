package com.dfs.metadata.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs the multipart expiry sweep. */
@Configuration
@EnableScheduling
public class MetadataSchedulingConfiguration {
}
