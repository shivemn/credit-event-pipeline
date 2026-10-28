package com.shivemn.ccp.pipeline;

import com.shivemn.ccp.model.CreditEvent;
import com.shivemn.ccp.model.EventType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/** Generates random credit events so the pipeline can be exercised without any real data. */
@Component
public class SyntheticEventGenerator {

    private static final EventType[] TYPES = EventType.values();

    public List<CreditEvent> generate(int count, int borrowerPool) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<CreditEvent> events = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String borrowerId = "B%06d".formatted(random.nextInt(borrowerPool));
            String lenderId = "L%03d".formatted(random.nextInt(50));
            EventType type = TYPES[random.nextInt(TYPES.length)];
            double amount = Math.round(random.nextDouble(1_000, 500_000) * 100) / 100.0;
            events.add(new CreditEvent(UUID.randomUUID().toString(), borrowerId, lenderId, type, amount, Instant.now()));
        }
        return events;
    }
}
