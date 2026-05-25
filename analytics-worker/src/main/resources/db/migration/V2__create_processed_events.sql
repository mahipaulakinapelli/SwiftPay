-- =====================================================================
-- SwiftPay Analytics — Consumer Inbox
-- The analytics-worker consumes payment-completed and projects into
-- analytics_transactions. This table dedupes Kafka redeliveries before
-- they hit the projection logic.
-- =====================================================================

CREATE TABLE processed_events (
    id              UUID            PRIMARY KEY,
    event_id        UUID            NOT NULL,
    consumer_group  VARCHAR(80)     NOT NULL,
    topic           VARCHAR(160)    NOT NULL,
    partition       INTEGER         NOT NULL,
    offset_value    BIGINT          NOT NULL,
    event_type      VARCHAR(160)    NOT NULL,
    aggregate_id    VARCHAR(64)     NOT NULL,
    checksum        VARCHAR(64),
    status          VARCHAR(24)     NOT NULL,
    last_error      TEXT,
    processed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_inbox_event_group UNIQUE (event_id, consumer_group),
    CONSTRAINT chk_inbox_status     CHECK (status IN ('PROCESSING','PROCESSED','FAILED','SKIPPED_DUPLICATE')),
    CONSTRAINT chk_inbox_partition  CHECK (partition >= 0)
);

CREATE INDEX idx_inbox_group_status ON processed_events (consumer_group, status);
CREATE INDEX idx_inbox_aggregate    ON processed_events (aggregate_id);
CREATE INDEX idx_inbox_topic_offset ON processed_events (topic, partition, offset_value);