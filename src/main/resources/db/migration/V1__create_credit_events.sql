-- Every credit event, kept permanently. The Kafka topic is the live stream; this table is the history.
CREATE TABLE credit_events (
    event_id        TEXT PRIMARY KEY,          -- the same event arriving twice is stored once
    borrower_id     TEXT           NOT NULL,
    lender_id       TEXT,
    event_type      TEXT           NOT NULL,
    amount          NUMERIC(18, 2) NOT NULL,
    occurred_at     TIMESTAMPTZ    NOT NULL,
    kafka_partition INTEGER,
    kafka_offset    BIGINT,
    archived_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- One person's timeline, newest first.
CREATE INDEX idx_credit_events_borrower ON credit_events (borrower_id, occurred_at, event_id);

-- Reading everything in order, page by page, when credit files are rebuilt.
CREATE INDEX idx_credit_events_time ON credit_events (occurred_at, event_id);
