package com.shivemn.ccp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shivemn.ccp.config.IgniteConfig;
import com.shivemn.ccp.model.CreditEvent;
import com.shivemn.ccp.model.CreditProfile;
import com.shivemn.ccp.model.EventType;
import com.shivemn.ccp.service.ProfileService;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProfileServiceTest {

    private static final AtomicLong EVENT_SEQUENCE = new AtomicLong();

    private static Ignite ignite;
    private static ProfileService service;

    @BeforeAll
    static void start() {
        IgniteConfig config = new IgniteConfig();
        // In-memory only, on its own discovery port so it never joins a running copy of the app.
        ignite = config.ignite("ccp-unit-test", false, 47600, "unused");
        IgniteCache<String, CreditProfile> cache = config.profileCache(ignite);
        service = new ProfileService(cache);
    }

    @AfterAll
    static void stop() {
        ignite.close();
    }

    private static CreditEvent event(String borrower, EventType type, double amount) {
        // Every event needs its own ID, because events with an ID the profile has already seen are ignored.
        return new CreditEvent("e-" + EVENT_SEQUENCE.incrementAndGet(), borrower, "L001", type, amount, Instant.now());
    }

    @Test
    void appliesEventsToBuildAProfile() {
        service.apply(event("unit-1", EventType.ACCOUNT_OPENED, 100_000));
        service.apply(event("unit-1", EventType.PAYMENT_ON_TIME, 5_000));
        service.apply(event("unit-1", EventType.PAYMENT_ON_TIME, 5_000));
        service.apply(event("unit-1", EventType.PAYMENT_DELAYED, 5_000));

        CreditProfile p = service.find("unit-1").orElseThrow();
        assertEquals(1, p.getActiveAccounts());
        assertEquals(100_000, p.getTotalExposure(), 0.001);
        assertEquals(2, p.getOnTimePayments());
        assertEquals(1, p.getDelayedPayments());
        assertEquals(4, p.getEventsApplied());
        assertEquals(2.0 / 3.0, p.getPaymentReliability(), 0.001);
    }

    @Test
    void closingAnAccountReducesExposure() {
        service.apply(event("unit-2", EventType.ACCOUNT_OPENED, 50_000));
        service.apply(event("unit-2", EventType.ACCOUNT_CLOSED, 50_000));

        CreditProfile p = service.find("unit-2").orElseThrow();
        assertEquals(0, p.getActiveAccounts());
        assertEquals(1, p.getClosedAccounts());
        assertEquals(0, p.getTotalExposure(), 0.001);
    }

    @Test
    void concurrentUpdatesToOneBorrowerAreNotLost() throws Exception {
        int threads = 8;
        int perThread = 250;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                for (int i = 0; i < perThread; i++) {
                    service.apply(event("unit-3", EventType.PAYMENT_ON_TIME, 1));
                }
            });
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        CreditProfile p = service.find("unit-3").orElseThrow();
        assertEquals(threads * perThread, p.getOnTimePayments());
        assertEquals(threads * perThread, p.getEventsApplied());
    }

    @Test
    void topProfilesAreSortedByEventsAppliedUsingSql() {
        for (int i = 0; i < 5; i++) {
            service.apply(event("sql-busy", EventType.PAYMENT_ON_TIME, 1));
        }
        for (int i = 0; i < 2; i++) {
            service.apply(event("sql-quiet", EventType.PAYMENT_ON_TIME, 1));
        }

        List<CreditProfile> top = service.topProfiles(1000);
        List<String> ids = top.stream().map(CreditProfile::getBorrowerId).toList();
        assertTrue(ids.indexOf("sql-busy") >= 0 && ids.indexOf("sql-quiet") >= 0);
        assertTrue(ids.indexOf("sql-busy") < ids.indexOf("sql-quiet"));
        for (int i = 1; i < top.size(); i++) {
            assertTrue(top.get(i - 1).getEventsApplied() >= top.get(i).getEventsApplied());
        }
        assertEquals(1, service.topProfiles(1).size());
    }

    @Test
    void portfolioAndAtRiskComeFromSqlAggregates() {
        service.apply(event("risk-1", EventType.PAYMENT_DELAYED, 1));
        service.apply(event("risk-1", EventType.PAYMENT_DELAYED, 1));
        service.apply(event("risk-1", EventType.PAYMENT_DELAYED, 1));
        service.apply(event("risk-1", EventType.PAYMENT_ON_TIME, 1));

        List<ProfileService.AtRiskBorrower> atRisk = service.atRisk(1000);
        ProfileService.AtRiskBorrower mine = atRisk.stream()
                .filter(b -> b.borrowerId().equals("risk-1")).findFirst().orElseThrow();
        assertEquals(3, mine.delayedPayments());
        assertEquals(1, mine.onTimePayments());

        ProfileService.Portfolio portfolio = service.portfolio();
        assertTrue(portfolio.borrowers() >= 1);
        assertTrue(portfolio.delayedPayments() >= 3);
        assertFalse(portfolio.onTimeRate() < 0 || portfolio.onTimeRate() > 1);
    }

    @Test
    void sameEventDeliveredTwiceIsAppliedOnce() {
        CreditEvent original = new CreditEvent("dup-1", "dedupe-1", "L001", EventType.PAYMENT_ON_TIME, 10, Instant.now());

        assertTrue(service.apply(original));
        assertFalse(service.apply(original));

        CreditProfile p = service.find("dedupe-1").orElseThrow();
        assertEquals(1, p.getEventsApplied());
        assertEquals(1, p.getOnTimePayments());
    }
}
