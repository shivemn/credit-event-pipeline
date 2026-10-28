package com.shivemn.ccp.archive;

import com.shivemn.ccp.model.CreditEvent;
import com.shivemn.ccp.model.EventType;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The permanent record of every credit event, in PostgreSQL.
 *
 * <p>Writing is idempotent: {@code event_id} is the primary key and a repeat is skipped with
 * {@code ON CONFLICT DO NOTHING}, so reading the same Kafka messages twice cannot create duplicates.
 */
public class EventArchive {

    private static final String INSERT = """
            INSERT INTO credit_events
                (event_id, borrower_id, lender_id, event_type, amount, occurred_at, kafka_partition, kafka_offset)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """;

    private static final String PAGE = """
            SELECT event_id, borrower_id, lender_id, event_type, amount, occurred_at
            FROM credit_events
            WHERE (occurred_at, event_id) > (?, ?)
            ORDER BY occurred_at, event_id
            LIMIT ?
            """;

    private static final String HISTORY = """
            SELECT event_id, event_type, amount, occurred_at, kafka_partition, kafka_offset
            FROM credit_events
            WHERE borrower_id = ?
            ORDER BY occurred_at DESC, event_id DESC
            LIMIT ?
            """;

    /** One event together with where it came from in Kafka. */
    public record Row(CreditEvent event, int partition, long offset) {
    }

    /** One line in a person's history. */
    public record HistoryRow(String eventId, String type, double amount, long occurredAtEpochMs,
                             Integer partition, Long offset) {
    }

    /** A position in the archive, used to read it in order, page by page. */
    public record Cursor(Instant occurredAt, String eventId) {
        public static Cursor start() {
            return new Cursor(Instant.EPOCH, "");
        }
    }

    private final JdbcTemplate jdbc;
    private final AtomicLong stored;

    public EventArchive(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        Long existing = jdbc.queryForObject("SELECT count(*) FROM credit_events", Long.class);
        this.stored = new AtomicLong(existing == null ? 0 : existing);
    }

    /** Stores a batch in one round trip. Returns how many were new (the rest were already archived). */
    public int store(List<Row> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        int[] results = jdbc.batchUpdate(INSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Row row = rows.get(i);
                CreditEvent e = row.event();
                ps.setString(1, e.eventId());
                ps.setString(2, e.borrowerId());
                ps.setString(3, e.lenderId());
                ps.setString(4, e.type().name());
                ps.setBigDecimal(5, BigDecimal.valueOf(e.amount()));
                ps.setTimestamp(6, Timestamp.from(e.occurredAt() == null ? Instant.now() : e.occurredAt()));
                ps.setInt(7, row.partition());
                ps.setLong(8, row.offset());
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
        int added = 0;
        for (int result : results) {
            if (result > 0) {
                added++;
            }
        }
        stored.addAndGet(added);
        return added;
    }

    /** How many events are in the archive (counted once at start, then kept up to date). */
    public long storedEvents() {
        return stored.get();
    }

    /** One person's events, newest first. */
    public List<HistoryRow> history(String borrowerId, int limit) {
        return jdbc.query(HISTORY, (rs, n) -> new HistoryRow(
                rs.getString("event_id"),
                rs.getString("event_type"),
                rs.getBigDecimal("amount").doubleValue(),
                rs.getTimestamp("occurred_at").getTime(),
                (Integer) rs.getObject("kafka_partition"),
                (Long) rs.getObject("kafka_offset")), borrowerId, limit);
    }

    /** The next {@code size} events in time order after {@code after}. Empty when the end is reached. */
    public List<CreditEvent> page(Cursor after, int size) {
        return jdbc.query(PAGE, (rs, n) -> new CreditEvent(
                rs.getString("event_id"),
                rs.getString("borrower_id"),
                rs.getString("lender_id"),
                EventType.valueOf(rs.getString("event_type")),
                rs.getBigDecimal("amount").doubleValue(),
                rs.getTimestamp("occurred_at").toInstant()), Timestamp.from(after.occurredAt()), after.eventId(), size);
    }
}
