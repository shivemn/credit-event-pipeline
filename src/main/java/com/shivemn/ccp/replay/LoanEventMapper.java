package com.shivemn.ccp.replay;

import com.shivemn.ccp.model.CreditEvent;
import com.shivemn.ccp.model.EventType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns one row of a public loan dataset (Lending Club style columns) into the short life story of that
 * loan: it is opened, some payments are made, and it may be closed.
 *
 * <p>The dataset has one row per loan and no person id, so loans are spread over a fixed pool of borrowers
 * by hashing the loan id. That way each borrower ends up with several loans, like a real credit file.
 * Payments are not in the dataset either: they are inferred from the loan's final status.
 *
 * <p>Columns used: id, loan_amnt, loan_status (required); installment, grade (optional).
 */
public class LoanEventMapper {

    private final int borrowerPool;

    public LoanEventMapper(int borrowerPool) {
        this.borrowerPool = Math.max(1, borrowerPool);
    }

    /** Returns no events when the row has no usable id, amount or status (for example a footer line). */
    public List<CreditEvent> toEvents(Map<String, String> row, int lap) {
        String loanId = clean(row.get("id"));
        String status = clean(row.get("loan_status"));
        Double amount = number(row.get("loan_amnt"));
        if (loanId.isEmpty() || status.isEmpty() || amount == null || amount <= 0) {
            return List.of();
        }
        Double installment = number(row.get("installment"));
        double payment = installment != null && installment > 0 ? installment : Math.round(amount / 36 * 100) / 100.0;

        String grade = clean(row.get("grade"));
        String lenderId = "L-" + (grade.isEmpty() ? "X" : grade.toUpperCase());
        String borrowerId = "B%06d".formatted(Math.floorMod(loanId.hashCode(), borrowerPool));

        // How the loan story goes, based on its final status.
        int onTime;
        int delayed;
        boolean closed;
        String lower = status.toLowerCase();
        if (lower.contains("fully paid")) {
            onTime = 3; delayed = 0; closed = true;
        } else if (lower.contains("charged off") || lower.contains("default")) {
            onTime = 1; delayed = 2; closed = true;
        } else if (lower.contains("late") || lower.contains("grace")) {
            onTime = 2; delayed = 1; closed = false;
        } else {
            onTime = 3; delayed = 0; closed = false; // "Current" and anything unknown
        }

        List<CreditEvent> events = new ArrayList<>();
        int step = 0;
        events.add(event(loanId, lap, step++, borrowerId, lenderId, EventType.ACCOUNT_OPENED, amount));
        for (int i = 0; i < onTime; i++) {
            events.add(event(loanId, lap, step++, borrowerId, lenderId, EventType.PAYMENT_ON_TIME, payment));
        }
        for (int i = 0; i < delayed; i++) {
            events.add(event(loanId, lap, step++, borrowerId, lenderId, EventType.PAYMENT_DELAYED, payment));
        }
        if (closed) {
            events.add(event(loanId, lap, step, borrowerId, lenderId, EventType.ACCOUNT_CLOSED, 0));
        }
        return events;
    }

    private static CreditEvent event(String loanId, int lap, int step, String borrowerId, String lenderId,
                                     EventType type, double amount) {
        // The id is stable for a given loan, step and lap, so a repeat of the same row would be caught as a duplicate.
        String eventId = "loan-%s-%d-lap%d".formatted(loanId, step, lap);
        return new CreditEvent(eventId, borrowerId, lenderId, type, amount, Instant.now());
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static Double number(String value) {
        try {
            return value == null || value.isBlank() ? null : Double.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
