package com.shivemn.ccp.config;

import com.shivemn.ccp.service.PipelineMetrics;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * What happens to an event that cannot be processed.
 *
 * <p>A record that fails is retried 3 times, one second apart. If it still fails, or if it can never be read
 * at all (for example malformed JSON), it is copied to the dead-letter topic {@code <topic>.DLT} and the
 * consumer moves on. Nothing is silently lost, and one bad record cannot block the partition.
 */
@Configuration
public class KafkaErrorConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorConfig.class);

    /** Same partition count as the main topic: the recoverer sends each record to the same partition number. */
    @Bean
    public NewTopic deadLetterTopic(
            @Value("${pipeline.topic}") String topic,
            @Value("${pipeline.partitions}") int partitions) {
        return TopicBuilder.name(topic + ".DLT").partitions(partitions).replicas(1).build();
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> jsonTemplate,
            KafkaProperties kafkaProperties,
            PipelineMetrics metrics) {
        // Records that could not even be deserialized are sent on as their original bytes.
        Map<String, Object> bytesProps = kafkaProperties.buildProducerProperties(null);
        bytesProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        KafkaTemplate<String, byte[]> bytesTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(bytesProps));

        Map<Class<?>, KafkaOperations<? extends Object, ? extends Object>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, bytesTemplate);
        templates.put(Object.class, jsonTemplate);
        DeadLetterPublishingRecoverer publisher = new DeadLetterPublishingRecoverer(templates);

        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            log.warn("Moving record to the dead-letter topic: topic={} partition={} offset={} reason={}",
                    record.topic(), record.partition(), record.offset(), exception.getMessage());
            metrics.recordDeadLettered();
            publisher.accept(record, exception);
        };
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }
}
