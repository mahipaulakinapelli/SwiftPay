-- =====================================================================
-- SwiftPay Analytics — completed-transaction projection
-- Owned by analytics-worker; read-only for everything else.
-- =====================================================================

CREATE TABLE analytics_transactions (
    id                       BIGSERIAL       PRIMARY KEY,
    transaction_id           UUID            NOT NULL,
    sender_id                BIGINT          NOT NULL,
    receiver_id              BIGINT          NOT NULL,
    amount                   NUMERIC(19, 4)  NOT NULL,
    currency                 VARCHAR(3)      NOT NULL,
    sender_balance_after     NUMERIC(19, 4),
    receiver_balance_after   NUMERIC(19, 4),
    event_id                 UUID,
    occurred_at              TIMESTAMPTZ,
    recorded_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_an_tx_id   UNIQUE (transaction_id),
    CONSTRAINT chk_an_amount CHECK (amount > 0),
    CONSTRAINT chk_an_curr   CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_an_currency      ON analytics_transactions (currency);
CREATE INDEX idx_an_occurred_desc ON analytics_transactions (occurred_at DESC);
CREATE INDEX idx_an_sender        ON analytics_transactions (sender_id);
CREATE INDEX idx_an_receiver      ON analytics_transactions (receiver_id);
