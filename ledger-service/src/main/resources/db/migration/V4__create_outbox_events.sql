-- =====================================================================
-- SwiftPay Ledger — Outbox Pattern
-- Decouples DB commit from Kafka publish so payment-completed /
-- payment-failed events survive broker outages.
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

CREATE INDEX idx_outbox_pending_created
    ON outbox_events (created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_aggregate
    ON outbox_events (aggregate_type, aggregate_id);