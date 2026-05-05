CREATE TABLE audit_logs
(
    id          UUID         PRIMARY KEY  DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id   VARCHAR(255),
    details     TEXT,
    ip_address  VARCHAR(45),
    timestamp   TIMESTAMP    NOT NULL     DEFAULT now()
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs (timestamp DESC);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
