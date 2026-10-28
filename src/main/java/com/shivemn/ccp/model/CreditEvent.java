package com.shivemn.ccp.model;

import java.time.Instant;

/**
 * A single synthetic credit event reported by a lender about a borrower.
 * All data in this project is randomly generated.
 */
public record CreditEvent(
        String eventId,
        String borrowerId,
        String lenderId,
        EventType type,
        double amount,
        Instant occurredAt) {
}
