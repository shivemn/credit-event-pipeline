package com.shivemn.ccp;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shivemn.ccp.model.CreditEvent;
import com.shivemn.ccp.model.CreditProfile;
import com.shivemn.ccp.model.EventType;
import com.shivemn.ccp.pipeline.EventPublisher;
import com.shivemn.ccp.service.PipelineMetrics;
import com.shivemn.ccp.service.ProfileService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/** End to end: publish to Kafka, consume, and read the resulting profile back from Ignite. */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "pipeline.ignite.instance-name=ccp-integration-test",
        "pipeline.ignite.persistence-enabled=false",
        "pipeline.ignite.discovery-port=47600",
        "pipeline.archive.enabled=false"
})
@EmbeddedKafka(partitions = 3)
class PipelineIntegrationTest {

    @Autowired
    private EventPublisher publisher;

    @Autowired
    private ProfileService profiles;

    @Autowired
    private PipelineMetrics metrics;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void eventsFlowFromKafkaIntoIgnite() {
        String borrower = "it-borrower-1";
        publisher.publish(event(borrower, EventType.ACCOUNT_OPENED, 200_000));
        publisher.publish(event(borrower, EventType.PAYMENT_ON_TIME, 10_000));
        publisher.publish(event(borrower, EventType.PAYMENT_ON_TIME, 10_000));
        publisher.publish(event(borrower, EventType.PAYMENT_DELAYED, 10_000));

        await().atMost(Duration.ofSeconds(60)).ignoreExceptions().untilAsserted(() -> {
            CreditProfile p = profiles.find(borrower).orElseThrow();
            assertEquals(4, p.getEventsApplied());
            assertEquals(1, p.getActiveAccounts());
            assertEquals(2, p.getOnTimePayments());
            assertEquals(1, p.getDelayedPayments());
        });
    }

    @Test
    void anEventDeliveredTwiceIsCountedOnce() {
        String borrower = "it-borrower-dup";
        CreditEvent once = new CreditEvent("it-dup-1", borrower, "L001", EventType.PAYMENT_ON_TIME, 5_000, Instant.now());
        long duplicatesBefore = metrics.duplicates();

        publisher.publish(once);
        publisher.publish(once);

        // Wait until the second copy has been seen and ignored, then check the profile.
        await().atMost(Duration.ofSeconds(60)).ignoreExceptions().untilAsserted(() ->
                assertTrue(metrics.duplicates() > duplicatesBefore));
        assertEquals(1, profiles.find(borrower).orElseThrow().getEventsApplied());
    }

    @Test
    void messagesThatCannotBeReadGoToTheDeadLetterTopic() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(broker);
        try (Producer<String, String> producer = new DefaultKafkaProducerFactory<>(
                producerProps, new StringSerializer(), new StringSerializer()).createProducer()) {
            producer.send(new ProducerRecord<>("credit-events", "bad-key", "this is not valid json"));
            producer.flush();
        }

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("dlt-test-group", "true", broker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of("credit-events.DLT"));
            ConsumerRecord<String, String> failed =
                    KafkaTestUtils.getSingleRecord(consumer, "credit-events.DLT", Duration.ofSeconds(60));
            assertNotNull(failed);
            assertEquals("this is not valid json", failed.value());
        }
    }

    private static CreditEvent event(String borrower, EventType type, double amount) {
        return new CreditEvent("e-" + System.nanoTime(), borrower, "L001", type, amount, Instant.now());
    }
}
