package com.shivemn.ccp.web;

import com.shivemn.ccp.model.CreditProfile;
import com.shivemn.ccp.service.PipelineMetrics;
import com.shivemn.ccp.service.ProfileService;
import com.shivemn.ccp.service.StatsService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProfileController {

    private static final int MAX_TOP = 100;

    private final ProfileService profileService;
    private final PipelineMetrics metrics;
    private final StatsService statsService;

    public ProfileController(ProfileService profileService, PipelineMetrics metrics, StatsService statsService) {
        this.profileService = profileService;
        this.metrics = metrics;
        this.statsService = statsService;
    }

    @GetMapping("/profiles/{borrowerId}")
    public ResponseEntity<CreditProfile> profile(@PathVariable String borrowerId) {
        return profileService.find(borrowerId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Profiles with the most events applied. */
    @GetMapping("/profiles")
    public List<CreditProfile> topProfiles(@RequestParam(defaultValue = "10") int limit) {
        return profileService.topProfiles(Math.max(1, Math.min(limit, MAX_TOP)));
    }

    /** Portfolio totals and the most at-risk borrowers, both computed by Ignite SQL. */
    @GetMapping("/analytics")
    public Map<String, Object> analytics(@RequestParam(defaultValue = "5") int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("portfolio", profileService.portfolio());
        result.put("atRisk", profileService.atRisk(Math.max(1, Math.min(limit, MAX_TOP))));
        return result;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return statsService.stats();
    }

    /** The most recently processed events, newest first, with the partition and thread that handled them. */
    @GetMapping("/recent")
    public List<PipelineMetrics.ProcessedEvent> recent() {
        return metrics.recent();
    }
}
