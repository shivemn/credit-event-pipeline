package com.shivemn.ccp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shivemn.ccp.archive.EventArchive;
import com.shivemn.ccp.archive.RebuildService;
import com.shivemn.ccp.config.ArchiveConfig;
import com.shivemn.ccp.config.IgniteConfig;
import com.shivemn.ccp.model.CreditEvent;
import com.shivemn.ccp.model.CreditProfile;
import com.shivemn.ccp.model.EventType;
import com.shivemn.ccp.service.ProfileService;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.util.List;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs against a real PostgreSQL started in Docker. Skipped automatically when Docker is not running.
 * Covers the table migration, idempotent storing, history queries, and rebuilding Ignite from the archive.
 */
@Testcontainers(disabledWithoutDocker = true)
class ArchiveIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static HikariDataSource dataSource;
    private static EventArchive archive;
    private static Ignite ignite;
    private static ProfileService profiles;

    @BeforeAll
    static void start() {
        dataSource = new ArchiveConfig().archiveDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        archive = new EventArchive(new JdbcTemplate(dataSource));

        IgniteConfig config = new IgniteConfig();
        ignite = config.ignite("ccp-archive-test", false, 47800, "unused");
        IgniteCache<String, CreditProfile> cache = config.profileCache(ignite);
        profiles = new ProfileService(cache);
    }

    @AfterAll
    static void stop() {
        ignite.close();
        dataSource.close();
    }

    private static EventArchive.Row row(String eventId, String borrower, EventType type, double amount, Instant at) {
        return new EventArchive.Row(new CreditEvent(eventId, borrower, "L001", type, amount, at), 0, 0L);
    }

    @Test
    void storingTheSameEventTwiceKeepsOneCopy() {
        Instant now = Instant.now();
        List<EventArchive.Row> rows = List.of(
                row("idem-1", "idem-b", EventType.ACCOUNT_OPENED, 1000, now),
                row("idem-2", "idem-b", EventType.PAYMENT_ON_TIME, 100, now.plusSeconds(1)),
                row("idem-3", "idem-b", EventType.PAYMENT_ON_TIME, 100, now.plusSeconds(2)));
        long before = archive.storedEvents();

        assertEquals(3, archive.store(rows));
        assertEquals(0, archive.store(rows), "the second copy of each event must be skipped");
        assertEquals(before + 3, archive.storedEvents());
    }

    @Test
    void historyIsNewestFirst() {
        Instant now = Instant.now();
        archive.store(List.of(
                row("hist-1", "hist-b", EventType.ACCOUNT_OPENED, 500, now),
                row("hist-2", "hist-b", EventType.PAYMENT_ON_TIME, 50, now.plusSeconds(10)),
                row("hist-3", "hist-b", EventType.PAYMENT_DELAYED, 50, now.plusSeconds(20))));

        List<EventArchive.HistoryRow> history = archive.history("hist-b", 10);

        assertEquals(3, history.size());
        assertEquals("hist-3", history.get(0).eventId());
        assertEquals("PAYMENT_DELAYED", history.get(0).type());
        assertEquals("hist-1", history.get(2).eventId());
        assertTrue(archive.history("nobody", 10).isEmpty());
    }

    @Test
    void ignitePrefilledWithOtherDataIsRebuiltFromTheArchive() {
        Instant now = Instant.now();
        archive.store(List.of(
                row("rb-1", "rb-b", EventType.ACCOUNT_OPENED, 2000, now),
                row("rb-2", "rb-b", EventType.PAYMENT_ON_TIME, 100, now.plusSeconds(1)),
                row("rb-3", "rb-b", EventType.PAYMENT_ON_TIME, 100, now.plusSeconds(2)),
                row("rb-4", "rb-b", EventType.PAYMENT_DELAYED, 100, now.plusSeconds(3))));
        // A profile that is not in the archive must disappear, and rb-b must be rebuilt from scratch.
        profiles.apply(new CreditEvent("stray-1", "stray-b", "L001", EventType.ACCOUNT_OPENED, 1, now));
        profiles.apply(new CreditEvent("wrong-1", "rb-b", "L001", EventType.PAYMENT_DELAYED, 9, now));

        long replayed = new RebuildService(archive, profiles).rebuildNow();

        assertTrue(replayed >= 4);
        assertTrue(profiles.find("stray-b").isEmpty());
        CreditProfile rebuilt = profiles.find("rb-b").orElseThrow();
        assertEquals(1, rebuilt.getActiveAccounts());
        assertEquals(2, rebuilt.getOnTimePayments());
        assertEquals(1, rebuilt.getDelayedPayments());
    }
}
