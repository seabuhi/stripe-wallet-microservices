CREATE TABLE wallets
(
    id         UUID           PRIMARY KEY  DEFAULT gen_random_uuid(),
    user_id    UUID           NOT NULL     UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    balance    NUMERIC(19, 4) NOT NULL     DEFAULT 0.0000,
    currency   VARCHAR(3)     NOT NULL     DEFAULT 'USD',
    version    BIGINT         NOT NULL     DEFAULT 0,
    updated_at TIMESTAMP      NOT NULL     DEFAULT now()
);

CREATE INDEX idx_wallets_user_id ON wallets (user_id);
