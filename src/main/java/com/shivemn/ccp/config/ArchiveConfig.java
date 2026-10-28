package com.shivemn.ccp.config;

import com.shivemn.ccp.archive.EventArchive;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Connects to PostgreSQL, creates or updates the table with Flyway, and sets up the batch reader.
 * Only active when {@code pipeline.archive.enabled=true}.
 */
@Configuration
@ConditionalOnProperty(name = "pipeline.archive.enabled", havingValue = "true")
public class ArchiveConfig {

    @Bean(destroyMethod = "close")
    public HikariDataSource archiveDataSource(
            @Value("${pipeline.archive.jdbc-url}") String url,
            @Value("${pipeline.archive.username}") String username,
            @Value("${pipeline.archive.password}") String password) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("archive");
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(4);
        config.setInitializationFailTimeout(5_000);
        HikariDataSource dataSource;
        try {
            dataSource = new HikariDataSource(config);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Could not connect to PostgreSQL at " + url
                    + ". Start it with 'docker compose up -d', or run without it using pipeline.archive.enabled=false.", e);
        }
        // Creates the credit_events table the first time, and applies any later changes in order.
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        return dataSource;
    }

    @Bean
    public JdbcTemplate archiveJdbcTemplate(HikariDataSource archiveDataSource) {
        return new JdbcTemplate(archiveDataSource);
    }

    @Bean
    public EventArchive eventArchive(JdbcTemplate archiveJdbcTemplate) {
        return new EventArchive(archiveJdbcTemplate);
    }

    /**
     * Reads the topic in batches. If PostgreSQL is down the same batch is retried every 2 seconds, without
     * giving up, so no event is skipped. Kafka keeps the messages until the database is back.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> archiveListenerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            @Value("${pipeline.partitions}") int partitions) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.setConcurrency(partitions);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(2000L, FixedBackOff.UNLIMITED_ATTEMPTS)));
        return factory;
    }
}
