package com.shivemn.ccp.web;

import com.shivemn.ccp.archive.EventArchive;
import com.shivemn.ccp.archive.RebuildService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The PostgreSQL history: archive status, one person's timeline, and rebuilding credit files from it. */
@RestController
@RequestMapping("/api")
public class ArchiveController {

    private static final int MAX_HISTORY = 200;

    private final ObjectProvider<EventArchive> archiveProvider;
    private final ObjectProvider<RebuildService> rebuildProvider;
    private final boolean rebuildAllowed;

    public ArchiveController(ObjectProvider<EventArchive> archiveProvider,
                             ObjectProvider<RebuildService> rebuildProvider,
                             @Value("${pipeline.admin.rebuild-enabled:false}") boolean rebuildAllowed) {
        this.archiveProvider = archiveProvider;
        this.rebuildProvider = rebuildProvider;
        this.rebuildAllowed = rebuildAllowed;
    }

    @GetMapping("/archive")
    public Map<String, Object> status() {
        EventArchive archive = archiveProvider.getIfAvailable();
        RebuildService rebuild = rebuildProvider.getIfAvailable();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", archive != null);
        status.put("storedEvents", archive == null ? 0 : archive.storedEvents());
        status.put("rebuildAllowed", archive != null && rebuildAllowed);
        status.put("rebuild", rebuild == null ? null : rebuild.status());
        return status;
    }

    @GetMapping("/history/{borrowerId}")
    public ResponseEntity<List<EventArchive.HistoryRow>> history(
            @PathVariable String borrowerId, @RequestParam(defaultValue = "30") int limit) {
        EventArchive archive = archiveProvider.getIfAvailable();
        if (archive == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(archive.history(borrowerId, Math.max(1, Math.min(limit, MAX_HISTORY))));
    }

    /** Empties Ignite and re-applies the whole history. Off unless pipeline.admin.rebuild-enabled=true. */
    @PostMapping("/archive/rebuild")
    public ResponseEntity<Map<String, Object>> rebuild() {
        RebuildService rebuild = rebuildProvider.getIfAvailable();
        if (rebuild == null || !rebuildAllowed) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Rebuild is switched off. Set pipeline.admin.rebuild-enabled=true to allow it."));
        }
        if (!rebuild.start()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "A rebuild is already running."));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(rebuild.status());
    }
}
