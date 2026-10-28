package com.shivemn.ccp.service;

import com.shivemn.ccp.archive.EventArchive;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Builds the headline numbers shown on the dashboard. Used by /api/stats and by the live stream. */
@Component
public class StatsService {

    private final ProfileService profileService;
    private final PipelineMetrics metrics;
    private final ObjectProvider<EventArchive> archiveProvider;

    public StatsService(ProfileService profileService, PipelineMetrics metrics,
                        ObjectProvider<EventArchive> archiveProvider) {
        this.profileService = profileService;
        this.metrics = metrics;
        this.archiveProvider = archiveProvider;
    }

    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("publishedEvents", metrics.published());
        stats.put("processedEvents", profileService.processedEvents());
        stats.put("cachedProfiles", profileService.profileCount());
        stats.put("duplicateEvents", metrics.duplicates());
        stats.put("deadLetteredEvents", metrics.deadLettered());
        stats.put("processedPerPartition", metrics.processedPerPartition());
        EventArchive archive = archiveProvider.getIfAvailable();
        if (archive != null) {
            stats.put("archivedEvents", archive.storedEvents());
        }
        return stats;
    }
}
