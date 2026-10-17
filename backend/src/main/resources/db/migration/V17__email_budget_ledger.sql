-- Per-user ledger of emails triggered by that user's actions (invitations, draw results), the
-- basis of the rolling app.notifications.email-budget-per-user. Kept apart from votes and
-- participants so deleting either cannot free budget; rows past the window are purged.
CREATE TABLE email_budget_ledger (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recipients INT         NOT NULL CHECK (recipients > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_budget_ledger_user_created ON email_budget_ledger (user_id, created_at);
CREATE INDEX idx_email_budget_ledger_created ON email_budget_ledger (created_at);
