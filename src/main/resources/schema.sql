CREATE TABLE IF NOT EXISTS accounts (
                                        account_id BIGSERIAL PRIMARY KEY,
                                        customer_id BIGINT NOT NULL,
                                        country VARCHAR(100) NOT NULL
    );

CREATE TABLE IF NOT EXISTS balances (
                                        balance_id BIGSERIAL PRIMARY KEY,
                                        account_id BIGINT NOT NULL,
                                        currency VARCHAR(3) NOT NULL,
    available_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    CONSTRAINT fk_balances_account
    FOREIGN KEY (account_id) REFERENCES accounts(account_id)
    ON DELETE CASCADE,
    CONSTRAINT uq_balances_account_currency
    UNIQUE (account_id, currency),
    CONSTRAINT chk_balances_currency
    CHECK (currency IN ('EUR', 'SEK', 'GBP', 'USD')),
    CONSTRAINT chk_balances_available_amount
    CHECK (available_amount >= 0)
    );

CREATE TABLE IF NOT EXISTS transactions (
                                            transaction_id BIGSERIAL PRIMARY KEY,
                                            account_id BIGINT NOT NULL,
                                            idempotency_key VARCHAR(100) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    direction VARCHAR(3) NOT NULL,
    description VARCHAR(255) NOT NULL,
    balance_after_transaction NUMERIC(19, 2) NOT NULL,
    CONSTRAINT fk_transactions_account
    FOREIGN KEY (account_id) REFERENCES accounts(account_id) ON DELETE CASCADE,
    CONSTRAINT uq_transactions_account_idempotency_key
    UNIQUE (account_id, idempotency_key),
    CONSTRAINT chk_transactions_amount
    CHECK (amount > 0),
    CONSTRAINT chk_transactions_currency
    CHECK (currency IN ('EUR', 'SEK', 'GBP', 'USD')),
    CONSTRAINT chk_transactions_direction
    CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT chk_transactions_balance_after_transaction
    CHECK (balance_after_transaction >= 0)
    );

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_transactions_account_id
    ON transactions(account_id);

CREATE INDEX IF NOT EXISTS idx_transactions_account_idempotency_key
    ON transactions(account_id, idempotency_key);

CREATE TABLE IF NOT EXISTS outbox_events (
                                             outbox_event_id BIGSERIAL PRIMARY KEY,
                                             event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    exchange_name VARCHAR(255) NOT NULL,
    routing_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    next_retry_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_outbox_status
    CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
    );

CREATE INDEX IF NOT EXISTS idx_outbox_status_next_retry
    ON outbox_events(status, next_retry_at, outbox_event_id);