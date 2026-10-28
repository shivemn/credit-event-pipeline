package com.shivemn.ccp.archive;

import com.shivemn.ccp.model.CreditEvent;
import com.shivemn.ccp.service.ProfileService;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Rebuilds every credit file in Ignite from the history in PostgreSQL: empty the cache, then re-apply every
 * archived event in time order. This is what makes Ignite a disposable fast layer instead of the only copy.
 *
 * <p>Run it while no events are arriving. Events that arrive during a rebuild could be counted twice.
 */
@Service
@ConditionalOnProperty(name = "pipeline.archive.enabled", havingValue = "true")
public class RebuildService {

    private static final Logger log = LoggerFactory.getLogger(RebuildService.class);
    private static final int PAGE_SIZE = 5_000;

    private final EventArchive archive;
    private final ProfileService profiles;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "rebuild"));
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong replayed = new AtomicLong();
    private volatile String error;

    public RebuildService(EventArchive archive, ProfileService profiles) {
        this.archive = archive;
        this.profiles = profiles;
    }

    /** Starts a rebuild in the background. Returns false if one is already running. */
    public boolean start() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        replayed.set(0);
        error = null;
        executor.submit(() -> {
            try {
                long count = rebuildNow();
                log.info("Rebuilt credit files from {} archived events", count);
            } catch (RuntimeException e) {
                log.error("Rebuild failed", e);
                error = "Rebuild failed: " + e.getMessage();
            } finally {
                running.set(false);
            }
        });
        return true;
    }

    /** Does the whole rebuild on the calling thread. Returns how many events were re-applied. */
    public long rebuildNow() {
        profiles.clearAll();
        replayed.set(0);
        EventArchive.Cursor cursor = EventArchive.Cursor.start();
        while (true) {
            List<CreditEvent> page = archive.page(cursor, PAGE_SIZE);
            if (page.isEmpty()) {
                return replayed.get();
            }
            for (CreditEvent event : page) {
                profiles.apply(event);
            }
            replayed.addAndGet(page.size());
            CreditEvent last = page.get(page.size() - 1);
            cursor = new EventArchive.Cursor(last.occurredAt(), last.eventId());
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", running.get());
        status.put("replayed", replayed.get());
        status.put("error", error);
        return status;
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }
}
