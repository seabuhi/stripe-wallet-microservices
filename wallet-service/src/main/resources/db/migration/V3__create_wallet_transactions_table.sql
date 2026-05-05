CREATE TABLE wallet_transactions
(
    id                       UUID           PRIMARY KEY  DEFAULT gen_random_uuid(),
    wallet_id                UUID           NOT NULL     REFERENCES wallets (id) ON DELETE RESTRICT,
    type                     VARCHAR(30)    NOT NULL,
    status                   VARCHAR(30)    NOT NULL,
    amount                   NUMERIC(19, 4) NOT NULL,
    idempotency_key          VARCHAR(255),
    stripe_payment_intent_id VARCHAR(255),
    stripe_session_id        VARCHAR(255),
    related_transaction_id   UUID,
    description              TEXT,
    created_at               TIMESTAMP      NOT NULL     DEFAULT now()
);

CREATE INDEX idx_wallet_tx_wallet_id ON wallet_transactions (wallet_id);
CREATE INDEX idx_wallet_tx_idempotency_key ON wallet_transactions (idempotency_key);
CREATE INDEX idx_wallet_tx_stripe_session ON wallet_transactions (stripe_session_id);
CREATE INDEX idx_wallet_tx_created_at ON wallet_transactions (created_at DESC);
