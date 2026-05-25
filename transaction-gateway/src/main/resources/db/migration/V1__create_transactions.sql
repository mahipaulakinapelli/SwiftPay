-- =====================================================================
-- SwiftPay Transaction Gateway — Initial Schema
-- Owns: transactions (gateway-accepted, not yet settled)
-- =====================================================================

CREATE TABLE transactions (
    transaction_id   UUID            PRIMARY KEY,
    sender_id        BIGINT          NOT NULL,
    receiver_id      BIGINT          NOT NULL,
    amount           NUMERIC(19, 4)  NOT NULL,
    currency         VARCHAR(3)      NOT NULL,
    status           VARCHAR(20)     NOT NULL,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    version          BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT chk_tx_amount     CHECK (amount > 0),
    CONSTRAINT chk_tx_not_self   CHECK (sender_id <> receiver_id),
    CONSTRAINT chk_tx_currency   CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_tx_status     CHECK (status IN ('PENDING','INITIATED','PROCESSING','COMPLETED','FAILED','REVERSED'))
);

CREATE INDEX idx_tx_sender_id           ON transactions (sender_id);
CREATE INDEX idx_tx_receiver_id         ON transactions (receiver_id);
CREATE INDEX idx_tx_status              ON transactions (status);
CREATE INDEX idx_tx_created_at_desc     ON transactions (created_at DESC);
CREATE INDEX idx_tx_sender_status_time  ON transactions (sender_id, status, created_at DESC);