-- =====================================================================
-- SwiftPay Ledger — Initial Schema
-- Tables: users, accounts, transactions, transaction_audit
-- =====================================================================

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id          BIGSERIAL    PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    full_name   VARCHAR(255) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_email   UNIQUE (email),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED'))
);

CREATE INDEX idx_users_status ON users (status);

-- ---------------------------------------------------------------------
-- accounts
-- ---------------------------------------------------------------------
CREATE TABLE accounts (
    id          BIGSERIAL       PRIMARY KEY,
    user_id     BIGINT          NOT NULL,
    currency    CHAR(3)         NOT NULL,
    balance     NUMERIC(19, 4)  NOT NULL DEFAULT 0,
    status      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    version     BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_accounts_user        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_accounts_user_curr   UNIQUE (user_id, currency),
    CONSTRAINT chk_accounts_balance    CHECK (balance >= 0),
    CONSTRAINT chk_accounts_currency   CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_accounts_status     CHECK (status IN ('ACTIVE','FROZEN','CLOSED'))
);

CREATE INDEX idx_accounts_user_id  ON accounts (user_id);
CREATE INDEX idx_accounts_currency ON accounts (currency);

-- ---------------------------------------------------------------------
-- transactions
-- ---------------------------------------------------------------------
CREATE TABLE transactions (
    transaction_id   UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id        BIGINT          NOT NULL,
    receiver_id      BIGINT          NOT NULL,
    amount           NUMERIC(19, 4)  NOT NULL,
    currency         CHAR(3)         NOT NULL,
    status           VARCHAR(20)     NOT NULL,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    version          BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_tx_sender       FOREIGN KEY (sender_id)   REFERENCES users (id),
    CONSTRAINT fk_tx_receiver     FOREIGN KEY (receiver_id) REFERENCES users (id),
    CONSTRAINT chk_tx_amount      CHECK (amount > 0),
    CONSTRAINT chk_tx_not_self    CHECK (sender_id <> receiver_id),
    CONSTRAINT chk_tx_currency    CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_tx_status      CHECK (status IN ('INITIATED','PROCESSING','COMPLETED','FAILED','REVERSED'))
);

CREATE INDEX idx_tx_sender_id            ON transactions (sender_id);
CREATE INDEX idx_tx_receiver_id          ON transactions (receiver_id);
CREATE INDEX idx_tx_status               ON transactions (status);
CREATE INDEX idx_tx_created_at_desc      ON transactions (created_at DESC);
CREATE INDEX idx_tx_sender_status_time   ON transactions (sender_id, status, created_at DESC);

-- ---------------------------------------------------------------------
-- transaction_audit (append-only history of state changes)
-- ---------------------------------------------------------------------
CREATE TABLE transaction_audit (
    id               BIGSERIAL    PRIMARY KEY,
    transaction_id   UUID         NOT NULL,
    previous_status  VARCHAR(20),
    new_status       VARCHAR(20)  NOT NULL,
    actor            VARCHAR(100) NOT NULL,
    reason           VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tx_audit_tx FOREIGN KEY (transaction_id) REFERENCES transactions (transaction_id)
);

CREATE INDEX idx_tx_audit_transaction_id  ON transaction_audit (transaction_id);
CREATE INDEX idx_tx_audit_created_at_desc ON transaction_audit (created_at DESC);