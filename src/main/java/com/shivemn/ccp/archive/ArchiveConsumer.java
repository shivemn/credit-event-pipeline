package com.shivemn.ccp.archive;

import com.shivemn.ccp.model.CreditEvent;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * A second, independent reader of the same Kafka topic. It has its own consumer group, so it keeps its own
 * place in the topic and never slows down the main consumer that updates Ignite. It receives events in batches
 * and writes each batch to PostgreSQL in one go.
 */
@Component
@ConditionalOnProperty(name = "pipeline.archive.enabled", havingValue = "true")
public class ArchiveConsumer {

    private final EventArchive archive;

    public ArchiveConsumer(EventArchive archive) {
        this.archive = archive;
    }

    @KafkaListener(
            topics = "${pipeline.topic}",
            groupId = "${pipeline.archive.group-id:credit-event-archiver}",
            containerFactory = "archiveListenerFactory")
    public void onBatch(List<ConsumerRecord<String, CreditEvent>> records) {
        List<EventArchive.Row> rows = new ArrayList<>(records.size());
        for (ConsumerRecord<String, CreditEvent> record : records) {
            // An unreadable message arrives here as null. The main consumer already sends it to the
            // dead-letter topic, so it is simply skipped.
            if (record.value() != null && record.value().type() != null && record.value().eventId() != null) {
                rows.add(new EventArchive.Row(record.value(), record.partition(), record.offset()));
            }
        }
        archive.store(rows);
    }
}
