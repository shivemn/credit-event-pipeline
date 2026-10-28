package com.shivemn.ccp.service;

import com.shivemn.ccp.model.CreditEvent;
import com.shivemn.ccp.model.CreditProfile;
import com.shivemn.ccp.model.EventType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.cache.processor.MutableEntry;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheEntryProcessor;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cache.query.FieldsQueryCursor;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.springframework.stereotype.Service;

/**
 * Builds and serves per-borrower credit profiles held in Ignite.
 *
 * <p>Updates go through an entry processor, which Ignite runs atomically on the node that owns the
 * key. That makes concurrent updates from several consumer threads safe without any explicit locking.
 */
@Service
public class ProfileService {

    private final IgniteCache<String, CreditProfile> cache;
    private final AtomicLong processedEvents = new AtomicLong();

    public ProfileService(IgniteCache<String, CreditProfile> cache) {
        this.cache = cache;
    }

    /**
     * Applies the event to its borrower's profile. Returns false when the event had already been applied
     * (a duplicate delivery), in which case nothing changes.
     */
    public boolean apply(CreditEvent event) {
        // Only primitives and Strings are passed, so nothing custom needs to be serialized as an argument.
        Boolean applied = cache.invoke(event.borrowerId(), new ApplyEventProcessor(),
                event.eventId(),
                event.type().name(),
                event.amount(),
                event.occurredAt() == null ? 0L : event.occurredAt().toEpochMilli());
        if (Boolean.TRUE.equals(applied)) {
            processedEvents.incrementAndGet();
            return true;
        }
        return false;
    }

    public Optional<CreditProfile> find(String borrowerId) {
        return Optional.ofNullable(cache.get(borrowerId));
    }

    /**
     * The profiles with the most events applied. Ignite runs this as SQL, sorted by an indexed column,
     * so it no longer has to read every entry in the cache.
     */
    public List<CreditProfile> topProfiles(int limit) {
        SqlFieldsQuery query = new SqlFieldsQuery(
                "SELECT _val FROM CreditProfile ORDER BY eventsApplied DESC LIMIT ?").setArgs(limit);
        List<CreditProfile> result = new ArrayList<>();
        try (FieldsQueryCursor<List<?>> cursor = cache.query(query)) {
            for (List<?> row : cursor) {
                result.add((CreditProfile) row.get(0));
            }
        }
        return result;
    }

    /** Portfolio-wide totals, computed by Ignite with SQL aggregate functions. */
    public Portfolio portfolio() {
        SqlFieldsQuery query = new SqlFieldsQuery(
                "SELECT COUNT(*), SUM(totalExposure), SUM(onTimePayments), SUM(delayedPayments) FROM CreditProfile");
        try (FieldsQueryCursor<List<?>> cursor = cache.query(query)) {
            List<?> row = cursor.getAll().get(0);
            long onTime = number(row.get(2)).longValue();
            long delayed = number(row.get(3)).longValue();
            long payments = onTime + delayed;
            return new Portfolio(
                    number(row.get(0)).longValue(),
                    number(row.get(1)).doubleValue(),
                    onTime,
                    delayed,
                    payments == 0 ? 1.0 : (double) onTime / payments);
        }
    }

    /** Borrowers with more late payments than on-time ones, worst first. */
    public List<AtRiskBorrower> atRisk(int limit) {
        SqlFieldsQuery query = new SqlFieldsQuery(
                "SELECT borrowerId, delayedPayments, onTimePayments FROM CreditProfile "
                        + "WHERE delayedPayments > onTimePayments ORDER BY delayedPayments DESC LIMIT ?").setArgs(limit);
        List<AtRiskBorrower> result = new ArrayList<>();
        try (FieldsQueryCursor<List<?>> cursor = cache.query(query)) {
            for (List<?> row : cursor) {
                result.add(new AtRiskBorrower(
                        (String) row.get(0), number(row.get(1)).longValue(), number(row.get(2)).longValue()));
            }
        }
        return result;
    }

    /** SQL returns NULL for SUM over no rows, so treat a missing number as zero. */
    private static Number number(Object value) {
        return value == null ? 0 : (Number) value;
    }

    public record Portfolio(long borrowers, double totalExposure, long onTimePayments, long delayedPayments,
                            double onTimeRate) {
    }

    public record AtRiskBorrower(String borrowerId, long delayedPayments, long onTimePayments) {
    }

    /** Removes every profile. Used only when rebuilding from the PostgreSQL history. */
    public void clearAll() {
        cache.clear();
    }

    public long processedEvents() {
        return processedEvents.get();
    }

    public int profileCount() {
        return cache.size(CachePeekMode.PRIMARY);
    }

    static class ApplyEventProcessor implements CacheEntryProcessor<String, CreditProfile, Boolean> {

        private static final long serialVersionUID = 1L;

        @Override
        public Boolean process(MutableEntry<String, CreditProfile> entry, Object... args) {
            String eventId = (String) args[0];
            EventType type = EventType.valueOf((String) args[1]);
            double amount = (Double) args[2];
            long occurredAt = (Long) args[3];

            CreditProfile profile = entry.exists() ? entry.getValue() : new CreditProfile(entry.getKey());
            // The duplicate check and the update happen in one atomic step, on the node that owns this borrower.
            boolean applied = profile.applyOnce(eventId, type, amount, occurredAt);
            if (applied) {
                entry.setValue(profile);
            }
            return applied;
        }
    }
}
