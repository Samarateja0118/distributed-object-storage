package com.dfs.common.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Activates Kafka listener infrastructure for event-driven services.
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
@EnableKafka
public class KafkaConfiguration {
}
