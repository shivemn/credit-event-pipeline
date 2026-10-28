package com.shivemn.ccp.pipeline;

import com.shivemn.ccp.model.CreditEvent;
import com.shivemn.ccp.service.PipelineMetrics;
import com.shivemn.ccp.service.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes credit events and folds them into borrower profiles in Ignite.
 *
 * <p>{@code concurrency} runs that many listener threads in one consumer group; Kafka spreads the
 * topic's partitions across them, so different borrowers are processed in parallel.
 */
@Component
public class CreditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CreditEventConsumer.class);

    private final ProfileService profileService;
    private final PipelineMetrics metrics;

    public CreditEventConsumer(ProfileService profileService, PipelineMetrics metrics) {
        this.profileService = profileService;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = "${pipeline.topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${pipeline.consumer-concurrency}")
    public void onEvent(CreditEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset) {
        log.debug("Applying {} for borrower {} (partition {}, offset {})",
                event.type(), event.borrowerId(), partition, offset);
        long startedAt = System.nanoTime();
        boolean applied = profileService.apply(event);
        if (applied) {
            metrics.recordProcessed(event, partition, offset, System.nanoTime() - startedAt);
        } else {
            log.debug("Ignoring duplicate event {} (partition {}, offset {})", event.eventId(), partition, offset);
            metrics.recordDuplicate();
        }
    }
}
