CREATE TABLE users
(
    id           UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    email        VARCHAR(255) NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,
    full_name    VARCHAR(255) NOT NULL,
    role         VARCHAR(20)  NOT NULL    DEFAULT 'USER',
    created_at   TIMESTAMP    NOT NULL    DEFAULT now()
);

CREATE INDEX idx_users_email ON users (email);
