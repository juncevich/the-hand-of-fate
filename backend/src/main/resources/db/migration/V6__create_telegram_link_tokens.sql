CREATE TABLE telegram_link_tokens (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token      VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_telegram_link_tokens_user_id    ON telegram_link_tokens (user_id);
CREATE INDEX idx_telegram_link_tokens_expires_at ON telegram_link_tokens (expires_at);
