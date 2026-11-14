package com.dfs.metadata.config;

import com.dfs.metadata.infrastructure.scheduling.MultipartExpirySweep;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

/**
 * Runs the multipart expiry sweep every {@code dfs.metadata.multipart.sweep-interval},
 * first after one interval rather than at startup, so a restart does not add a
 * sweep to whatever the previous instance just did.
 *
 * <p>Registered here from the bound {@link Duration} rather than with
 * {@code @Scheduled}, which would not accept {@code 15m}.
 */
@Configuration
@EnableScheduling
public class MetadataSchedulingConfiguration implements SchedulingConfigurer {

    private final MultipartExpirySweep sweep;
    private final MetadataProperties properties;

    public MetadataSchedulingConfiguration(MultipartExpirySweep sweep, MetadataProperties properties) {
        this.sweep = sweep;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        Duration interval = properties.multipart().sweepInterval();
        if (interval == null || interval.isNegative() || interval.isZero()) {
            throw new IllegalStateException("dfs.metadata.multipart.sweep-interval must be positive");
        }
        registrar.addFixedDelayTask(new FixedDelayTask(sweep::sweep, interval, interval));
    }
}
