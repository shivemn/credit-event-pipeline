package com.shivemn.ccp.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    /** Keyed by borrowerId, so all events for one borrower land on one partition and stay ordered. */
    @Bean
    public NewTopic creditEventsTopic(
            @Value("${pipeline.topic}") String topic,
            @Value("${pipeline.partitions}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }
}
