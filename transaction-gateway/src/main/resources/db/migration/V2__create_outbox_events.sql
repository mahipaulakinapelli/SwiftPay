-- =====================================================================
-- SwiftPay Transaction Gateway — Outbox Pattern
-- Decouples DB commit from Kafka publish: the business transaction writes
-- the event into this table, and a scheduled relay drains it asynchronously.
-- =====================================================================

CREATE TABLE outbox_events (
    id              UUID            PRIMARY KEY,
    aggregate_type  VARCHAR(64)     NOT NULL,
    aggregate_id    VARCHAR(64)     NOT NULL,
    event_type      VARCHAR(160)    NOT NULL,
    topic           VARCHAR(160)    NOT NULL,
    payload         JSONB           NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER         NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    CONSTRAINT chk_outbox_status      CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
    CONSTRAINT chk_outbox_retry_count CHECK (retry_count >= 0)
);

-- Partial index: the publisher scans only PENDING rows, oldest first.
CREATE INDEX idx_outbox_pending_created
    ON outbox_events (created_at)
    WHERE status = 'PENDING';

-- Operator lookup by business key (e.g. "find all events for transaction X").
CREATE INDEX idx_outbox_aggregate
    ON outbox_events (aggregate_type, aggregate_id);