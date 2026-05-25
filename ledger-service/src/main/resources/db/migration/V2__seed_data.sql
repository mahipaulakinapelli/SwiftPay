-- =====================================================================
-- SwiftPay Ledger — Seed Data
-- 5 users + multi-currency starter balances
-- =====================================================================

INSERT INTO users (email, full_name) VALUES
    ('alice@swiftpay.io',   'Alice Anderson'),
    ('bob@swiftpay.io',     'Bob Brown'),
    ('charlie@swiftpay.io', 'Charlie Chen'),
    ('diana@swiftpay.io',   'Diana Davis'),
    ('eve@swiftpay.io',     'Eve Evans');

INSERT INTO accounts (user_id, currency, balance) VALUES
    ((SELECT id FROM users WHERE email = 'alice@swiftpay.io'),   'USD',  10000.0000),
    ((SELECT id FROM users WHERE email = 'alice@swiftpay.io'),   'EUR',   5000.0000),
    ((SELECT id FROM users WHERE email = 'bob@swiftpay.io'),     'USD',   7500.0000),
    ((SELECT id FROM users WHERE email = 'charlie@swiftpay.io'), 'USD',   2500.0000),
    ((SELECT id FROM users WHERE email = 'diana@swiftpay.io'),   'INR', 200000.0000),
    ((SELECT id FROM users WHERE email = 'eve@swiftpay.io'),     'USD',    100.0000);