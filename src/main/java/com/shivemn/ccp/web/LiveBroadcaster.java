package com.shivemn.ccp.web;

import com.shivemn.ccp.service.PipelineMetrics;
import com.shivemn.ccp.service.StatsService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes the latest numbers to every open browser tab (Server-Sent Events), four times a second.
 * The browser keeps one connection open instead of asking again and again. When nobody is watching, nothing is sent.
 */
@Component
public class LiveBroadcaster {

    private static final long PUSH_EVERY_MS = 250;
    private static final long CONNECTION_TIMEOUT_MS = 30 * 60 * 1000L; // the browser reconnects by itself

    private final StatsService statsService;
    private final PipelineMetrics metrics;
    private final CopyOnWriteArrayList<SseEmitter> viewers = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "live-push"));

    public LiveBroadcaster(StatsService statsService, PipelineMetrics metrics) {
        this.statsService = statsService;
        this.metrics = metrics;
    }

    @PostConstruct
    void start() {
        scheduler.scheduleWithFixedDelay(this::push, PUSH_EVERY_MS, PUSH_EVERY_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT_MS);
        viewers.add(emitter);
        emitter.onCompletion(() -> viewers.remove(emitter));
        emitter.onTimeout(() -> viewers.remove(emitter));
        emitter.onError(e -> viewers.remove(emitter));
        send(emitter, snapshot()); // show something straight away
        return emitter;
    }

    private void push() {
        if (viewers.isEmpty()) {
            return;
        }
        try {
            Map<String, Object> snapshot = snapshot();
            for (SseEmitter viewer : viewers) {
                send(viewer, snapshot);
            }
        } catch (RuntimeException e) {
            // never let one bad tick stop the schedule
        }
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("stats", statsService.stats());
        snapshot.put("recent", metrics.recent());
        return snapshot;
    }

    private void send(SseEmitter viewer, Map<String, Object> snapshot) {
        try {
            viewer.send(SseEmitter.event().name("snapshot").data(snapshot));
        } catch (IOException | IllegalStateException e) {
            viewers.remove(viewer); // the tab was closed
        }
    }
}
