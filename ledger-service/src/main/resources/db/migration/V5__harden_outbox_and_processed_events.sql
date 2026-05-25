-- =====================================================================
-- SwiftPay Ledger — Outbox hardening + Consumer Inbox
-- 1. Add PUBLISHING / DEAD_LETTERED outbox states + last_attempt_at.
-- 2. Create processed_events (consumer-side dedup / inbox).
-- =====================================================================

ALTER TABLE outbox_events
    DROP CONSTRAINT chk_outbox_status;

ALTER TABLE outbox_events
    ADD CONSTRAINT chk_outbox_status
        CHECK (status IN ('PENDING','PUBLISHING','PUBLISHED','FAILED','DEAD_LETTERED'));

ALTER TABLE outbox_events
    ADD COLUMN last_attempt_at TIMESTAMPTZ;

CREATE INDEX idx_outbox_publishing_attempt
    ON outbox_events (last_attempt_at)
    WHERE status = 'PUBLISHING';

-- ---------------------------------------------------------------------
-- processed_events — consumer-side inbox / dedup table.
-- A row exists per (event_id, consumer_group) that this service has
-- ever attempted to process. The UNIQUE constraint is what blocks
-- duplicate Kafka redeliveries from re-running business logic.
-- ---------------------------------------------------------------------
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