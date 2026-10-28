package com.shivemn.ccp.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Builds the headline numbers shown on the dashboard. Used by /api/stats and by the live stream. */
@Component
public class StatsService {

    private final ProfileService profileService;
    private final PipelineMetrics metrics;

    public StatsService(ProfileService profileService, PipelineMetrics metrics) {
        this.profileService = profileService;
        this.metrics = metrics;
    }

    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("publishedEvents", metrics.published());
        stats.put("processedEvents", profileService.processedEvents());
        stats.put("cachedProfiles", profileService.profileCount());
        stats.put("duplicateEvents", metrics.duplicates());
        stats.put("deadLetteredEvents", metrics.deadLettered());
        stats.put("processedPerPartition", metrics.processedPerPartition());
        return stats;
    }
}
