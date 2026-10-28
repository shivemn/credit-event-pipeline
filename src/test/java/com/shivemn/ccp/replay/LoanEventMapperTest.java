package com.shivemn.ccp.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shivemn.ccp.model.CreditEvent;
import com.shivemn.ccp.model.EventType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LoanEventMapperTest {

    private final LoanEventMapper mapper = new LoanEventMapper(100);

    private static Map<String, String> row(String id, String amount, String status) {
        return Map.of("id", id, "loan_amnt", amount, "loan_status", status, "installment", "250.5", "grade", "b");
    }

    private static List<EventType> types(List<CreditEvent> events) {
        return events.stream().map(CreditEvent::type).collect(Collectors.toList());
    }

    @Test
    void fullyPaidLoanIsOpenedPaidOnTimeAndClosed() {
        List<CreditEvent> events = mapper.toEvents(row("1", "10000", "Fully Paid"), 0);
        assertEquals(List.of(EventType.ACCOUNT_OPENED, EventType.PAYMENT_ON_TIME, EventType.PAYMENT_ON_TIME,
                EventType.PAYMENT_ON_TIME, EventType.ACCOUNT_CLOSED), types(events));
        assertEquals(10000.0, events.get(0).amount());
        assertEquals(250.5, events.get(1).amount());
        assertEquals("L-B", events.get(0).lenderId());
    }

    @Test
    void chargedOffLoanHasDelayedPaymentsAndIsClosed() {
        List<EventType> types = types(mapper.toEvents(row("2", "5000", "Charged Off"), 0));
        assertEquals(2, types.stream().filter(t -> t == EventType.PAYMENT_DELAYED).count());
        assertEquals(EventType.ACCOUNT_CLOSED, types.get(types.size() - 1));
    }

    @Test
    void currentLoanStaysOpen() {
        List<EventType> types = types(mapper.toEvents(row("3", "5000", "Current"), 0));
        assertTrue(!types.contains(EventType.ACCOUNT_CLOSED));
    }

    @Test
    void eventsOfOneLoanShareABorrowerAndHaveUniqueIds() {
        List<CreditEvent> events = mapper.toEvents(row("4", "5000", "Fully Paid"), 0);
        assertEquals(1, events.stream().map(CreditEvent::borrowerId).distinct().count());
        assertEquals(events.size(), events.stream().map(CreditEvent::eventId).distinct().count());
    }

    @Test
    void secondLapUsesNewEventIdsSoTheyAreNotDroppedAsDuplicates() {
        String first = mapper.toEvents(row("5", "5000", "Current"), 0).get(0).eventId();
        String second = mapper.toEvents(row("5", "5000", "Current"), 1).get(0).eventId();
        assertTrue(!first.equals(second));
    }

    @Test
    void rowsWithoutUsableDataAreSkipped() {
        assertTrue(mapper.toEvents(row("", "5000", "Current"), 0).isEmpty());
        assertTrue(mapper.toEvents(row("6", "abc", "Current"), 0).isEmpty());
        assertTrue(mapper.toEvents(row("7", "5000", ""), 0).isEmpty());
    }
}
