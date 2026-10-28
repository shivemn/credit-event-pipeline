package com.shivemn.ccp.pipeline;

import com.shivemn.ccp.model.CreditEvent;
import com.shivemn.ccp.service.PipelineMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes credit events to Kafka, keyed by borrower so per-borrower ordering is preserved. */
@Component
public class EventPublisher {

    private final KafkaTemplate<String, CreditEvent> kafkaTemplate;
    private final PipelineMetrics metrics;
    private final String topic;

    public EventPublisher(KafkaTemplate<String, CreditEvent> kafkaTemplate,
                          PipelineMetrics metrics,
                          @Value("${pipeline.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.metrics = metrics;
        this.topic = topic;
    }

    public void publish(CreditEvent event) {
        kafkaTemplate.send(topic, event.borrowerId(), event);
        metrics.recordPublished();
    }
}
