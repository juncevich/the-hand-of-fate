-- Accounts must confirm their email before they can sign in; existing accounts (including the dev
-- demo user) predate the requirement and count as verified.
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;
UPDATE users SET email_verified_at = created_at;

-- One-time tokens mailed to the account's address: email verification and password reset.
-- Only the SHA-256 of the token is stored; one live token per user and purpose.
CREATE TABLE email_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose    VARCHAR(32) NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, purpose)
);

CREATE INDEX idx_email_tokens_expires_at ON email_tokens (expires_at);
