CREATE TABLE idempotency_keys
(
    id            UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    key           VARCHAR(255) NOT NULL,
    user_id       UUID         NOT NULL,
    endpoint      VARCHAR(255) NOT NULL,
    response_body TEXT,
    created_at    TIMESTAMP    NOT NULL     DEFAULT now(),
    expires_at    TIMESTAMP    NOT NULL,

    CONSTRAINT uq_idempotency_user_endpoint_key UNIQUE (key, user_id, endpoint)
);

CREATE INDEX idx_idempotency_user_id   ON idempotency_keys (user_id);
CREATE INDEX idx_idempotency_expires_at ON idempotency_keys (expires_at);
