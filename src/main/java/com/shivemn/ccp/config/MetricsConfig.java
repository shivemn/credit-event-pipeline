package com.shivemn.ccp.config;

import com.shivemn.ccp.service.PipelineMetrics;
import com.shivemn.ccp.service.ProfileService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Gauges are values that go up and down (unlike counters), read each time Prometheus scrapes. */
@Configuration
public class MetricsConfig {

    @Bean
    public MeterBinder pipelineGauges(ProfileService profiles, PipelineMetrics metrics) {
        return registry -> {
            Gauge.builder("pipeline.profiles.cached", profiles, ProfileService::profileCount)
                    .description("Borrower profiles held in the Ignite cache")
                    .register(registry);
            Gauge.builder("pipeline.events.in.flight",
                            () -> Math.max(0L, metrics.published() - profiles.processedEvents()))
                    .description("Events published to Kafka but not yet applied to Ignite")
                    .register(registry);
        };
    }
}
