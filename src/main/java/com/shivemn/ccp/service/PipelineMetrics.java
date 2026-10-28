package com.shivemn.ccp.service;

import com.shivemn.ccp.model.CreditEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/**
 * In-memory counters and a short history of recently processed events.
 * Used by the dashboard to show how events move through Kafka and the consumer threads.
 * The same numbers are also published to Micrometer, which Prometheus scrapes at /actuator/prometheus.
 */
@Component
public class PipelineMetrics {

    private static final int RECENT_LIMIT = 40;

    /** One processed event, with where it ran. Serialized as JSON for the dashboard. */
    public record ProcessedEvent(
            String borrowerId,
            String type,
            double amount,
            int partition,
            long offset,
            String thread,
            long processedAtEpochMs) {
    }

    private final LongAdder published = new LongAdder();
    private final LongAdder duplicates = new LongAdder();
    private final LongAdder deadLettered = new LongAdder();
    private final ConcurrentHashMap<Integer, LongAdder> perPartition = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<ProcessedEvent> recent = new ConcurrentLinkedDeque<>();

    private final MeterRegistry registry;
    private final Counter publishedCounter;
    private final Counter duplicateCounter;
    private final Counter deadLetterCounter;
    private final ConcurrentHashMap<Integer, Counter> processedCounters = new ConcurrentHashMap<>();
    private final Timer processingTimer;

    public PipelineMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.publishedCounter = Counter.builder("pipeline.events.published")
                .description("Events published to Kafka")
                .register(registry);
        this.duplicateCounter = Counter.builder("pipeline.events.duplicates")
                .description("Events ignored because they had already been applied")
                .register(registry);
        this.deadLetterCounter = Counter.builder("pipeline.events.dead.lettered")
                .description("Events that kept failing and were moved to the dead-letter topic")
                .register(registry);
        // Per-event work takes microseconds, so the histogram range starts well below the 1 ms default.
        this.processingTimer = Timer.builder("pipeline.event.processing")
                .description("Time to apply one event to the Ignite cache")
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofNanos(50_000))
                .maximumExpectedValue(Duration.ofSeconds(1))
                .register(registry);
    }

    public void recordPublished() {
        published.increment();
        publishedCounter.increment();
    }

    public void recordDuplicate() {
        duplicates.increment();
        duplicateCounter.increment();
    }

    public void recordDeadLettered() {
        deadLettered.increment();
        deadLetterCounter.increment();
    }

    public void recordProcessed(CreditEvent event, int partition, long offset, long elapsedNanos) {
        perPartition.computeIfAbsent(partition, p -> new LongAdder()).increment();
        processedCounters.computeIfAbsent(partition, p -> Counter.builder("pipeline.events.processed")
                .description("Events applied to Ignite, by Kafka partition")
                .tag("partition", String.valueOf(p))
                .register(registry)).increment();
        processingTimer.record(elapsedNanos, TimeUnit.NANOSECONDS);
        recent.addFirst(new ProcessedEvent(
                event.borrowerId(),
                event.type().name(),
                event.amount(),
                partition,
                offset,
                Thread.currentThread().getName(),
                System.currentTimeMillis()));
        while (recent.size() > RECENT_LIMIT) {
            recent.pollLast();
        }
    }

    public long published() {
        return published.sum();
    }

    public long duplicates() {
        return duplicates.sum();
    }

    public long deadLettered() {
        return deadLettered.sum();
    }

    /** Events processed so far, per Kafka partition, in partition order. */
    public Map<Integer, Long> processedPerPartition() {
        Map<Integer, Long> result = new TreeMap<>();
        perPartition.forEach((partition, count) -> result.put(partition, count.sum()));
        return result;
    }

    /** Most recent first. */
    public List<ProcessedEvent> recent() {
        return new ArrayList<>(recent);
    }
}
