-- =====================================================================
-- SwiftPay Transaction Gateway — Outbox hardening
-- 1. Add PUBLISHING (in-flight) and DEAD_LETTERED (terminal) states.
-- 2. Track last_attempt_at for exponential-backoff retry pacing.
-- =====================================================================

ALTER TABLE outbox_events
    DROP CONSTRAINT chk_outbox_status;

ALTER TABLE outbox_events
    ADD CONSTRAINT chk_outbox_status
        CHECK (status IN ('PENDING','PUBLISHING','PUBLISHED','FAILED','DEAD_LETTERED'));

ALTER TABLE outbox_events
    ADD COLUMN last_attempt_at TIMESTAMPTZ;

-- Rescue index: scheduled sweeper resets rows stuck in PUBLISHING.
CREATE INDEX idx_outbox_publishing_attempt
    ON outbox_events (last_attempt_at)
    WHERE status = 'PUBLISHING';