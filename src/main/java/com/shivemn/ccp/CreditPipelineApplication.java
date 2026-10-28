package com.shivemn.ccp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;

/**
 * The PostgreSQL archive is optional, so Spring Boot's automatic database setup is switched off here and
 * {@code ArchiveConfig} creates the connection itself, only when {@code pipeline.archive.enabled=true}.
 * This keeps every test and run without a database exactly as it was.
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        FlywayAutoConfiguration.class})
public class CreditPipelineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreditPipelineApplication.class, args);
    }
}
