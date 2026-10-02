CREATE TABLE wallets
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    balance    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT wallets_user_id_uk UNIQUE (user_id),
    CONSTRAINT wallets_balance_check CHECK (balance >= 0)
);

CREATE TABLE operations
(
    id             UUID PRIMARY KEY,
    type           VARCHAR(16) NOT NULL,
    from_wallet_id UUID REFERENCES wallets (id),
    to_wallet_id   UUID REFERENCES wallets (id),
    amount         BIGINT      NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT operations_type_check CHECK (type IN ('DEPOSIT', 'WITHDRAW', 'TRANSFER')),
    CONSTRAINT operations_amount_check CHECK (amount > 0),
    CONSTRAINT operations_parties_check CHECK (
        (type = 'DEPOSIT' AND from_wallet_id IS NULL AND to_wallet_id IS NOT NULL)
            OR (type = 'WITHDRAW' AND from_wallet_id IS NOT NULL AND to_wallet_id IS NULL)
            OR (type = 'TRANSFER' AND from_wallet_id IS NOT NULL AND to_wallet_id IS NOT NULL
            AND from_wallet_id <> to_wallet_id)
        )
);

CREATE INDEX operations_from_wallet_idx ON operations (from_wallet_id, created_at DESC);
CREATE INDEX operations_to_wallet_idx ON operations (to_wallet_id, created_at DESC);
