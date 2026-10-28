package com.shivemn.ccp.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import org.apache.ignite.cache.query.annotations.QuerySqlField;

/**
 * Aggregated, per-borrower view built up from the event stream and held in an Ignite cache.
 *
 * <p>Fields marked {@code @QuerySqlField} become columns of the SQL table {@code CreditProfile}.
 */
public class CreditProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** How many recent event IDs each profile remembers, to recognise events that arrive twice. */
    private static final int RECENT_EVENT_ID_LIMIT = 50;

    @QuerySqlField
    private String borrowerId;
    @QuerySqlField
    private int activeAccounts;
    @QuerySqlField
    private int closedAccounts;
    @QuerySqlField
    private int onTimePayments;
    @QuerySqlField
    private int delayedPayments;
    @QuerySqlField
    private double totalExposure;
    // Indexed: "busiest borrowers" sorts on this column.
    @QuerySqlField(index = true)
    private long eventsApplied;
    @QuerySqlField
    private long lastEventAtEpochMs;

    /** IDs of the last few events applied, oldest first. Not a SQL column and not shown in the API. */
    private ArrayList<String> recentEventIds = new ArrayList<>();

    public CreditProfile() {
    }

    public CreditProfile(String borrowerId) {
        this.borrowerId = borrowerId;
    }

    /** Applies one event to this profile. Mutates and returns this instance. */
    public CreditProfile apply(CreditEvent event) {
        Instant at = event.occurredAt();
        return apply(event.type(), event.amount(), at == null ? 0L : at.toEpochMilli());
    }

    /**
     * Applies an event unless this profile has already applied one with the same ID.
     * Kafka delivers at least once, so the same event can arrive twice (for example after a crash
     * between applying it and committing the offset). Returns false for such a duplicate.
     */
    public boolean applyOnce(String eventId, EventType type, double amount, long occurredAtEpochMs) {
        if (eventId != null) {
            if (recentEventIds.contains(eventId)) {
                return false;
            }
            recentEventIds.add(eventId);
            if (recentEventIds.size() > RECENT_EVENT_ID_LIMIT) {
                recentEventIds.remove(0);
            }
        }
        apply(type, amount, occurredAtEpochMs);
        return true;
    }

    /** Primitive-argument variant, used by the Ignite entry processor so no custom types are shipped. */
    public CreditProfile apply(EventType type, double amount, long occurredAtEpochMs) {
        switch (type) {
            case ACCOUNT_OPENED -> {
                activeAccounts++;
                totalExposure += amount;
            }
            case ACCOUNT_CLOSED -> {
                if (activeAccounts > 0) {
                    activeAccounts--;
                }
                closedAccounts++;
                totalExposure = Math.max(0, totalExposure - amount);
            }
            case PAYMENT_ON_TIME -> onTimePayments++;
            case PAYMENT_DELAYED -> delayedPayments++;
        }
        eventsApplied++;
        lastEventAtEpochMs = Math.max(lastEventAtEpochMs, occurredAtEpochMs);
        return this;
    }

    /** Share of payments made on time, between 0 and 1. Returns 1 when there are no payments yet. */
    public double getPaymentReliability() {
        int total = onTimePayments + delayedPayments;
        return total == 0 ? 1.0 : (double) onTimePayments / total;
    }

    public String getBorrowerId() {
        return borrowerId;
    }

    public int getActiveAccounts() {
        return activeAccounts;
    }

    public int getClosedAccounts() {
        return closedAccounts;
    }

    public int getOnTimePayments() {
        return onTimePayments;
    }

    public int getDelayedPayments() {
        return delayedPayments;
    }

    public double getTotalExposure() {
        return totalExposure;
    }

    public long getEventsApplied() {
        return eventsApplied;
    }

    public long getLastEventAtEpochMs() {
        return lastEventAtEpochMs;
    }
}
