package com.shivemn.ccp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.shivemn.ccp.config.IgniteConfig;
import com.shivemn.ccp.model.CreditEvent;
import com.shivemn.ccp.model.CreditProfile;
import com.shivemn.ccp.model.EventType;
import com.shivemn.ccp.service.ProfileService;
import java.nio.file.Path;
import java.time.Instant;
import org.apache.ignite.Ignite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves the data survives a restart, and that an event replayed after the restart is still ignored. */
class IgnitePersistenceTest {

    @TempDir
    Path dataDir;

    @Test
    void profilesSurviveARestartAndReplayedEventsAreIgnored() {
        IgniteConfig config = new IgniteConfig();
        CreditEvent event = new CreditEvent("persist-1", "persist-borrower", "L001",
                EventType.ACCOUNT_OPENED, 1_000, Instant.now());

        Ignite first = config.ignite("persistence-test-node", true, 47700, dataDir.toString());
        try {
            ProfileService service = new ProfileService(config.profileCache(first));
            service.apply(event);
        } finally {
            first.close();
        }

        // A brand new node with the same ID and the same folder: everything must come back from disk.
        Ignite second = config.ignite("persistence-test-node", true, 47700, dataDir.toString());
        try {
            ProfileService service = new ProfileService(config.profileCache(second));
            CreditProfile profile = service.find("persist-borrower").orElseThrow();
            assertEquals(1, profile.getEventsApplied());
            assertEquals(1, profile.getActiveAccounts());

            // Kafka may redeliver the last event after a crash. The profile remembers it already has it.
            assertFalse(service.apply(event));
            assertEquals(1, service.find("persist-borrower").orElseThrow().getEventsApplied());
        } finally {
            second.close();
        }
    }
}
