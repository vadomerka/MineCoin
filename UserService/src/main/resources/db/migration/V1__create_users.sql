CREATE TABLE users
(
    id            UUID PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(64),
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'BLOCKED', 'DELETED'))
);

CREATE UNIQUE INDEX users_username_lower_uidx ON users (lower(username));
CREATE UNIQUE INDEX users_email_lower_uidx ON users (lower(email));
