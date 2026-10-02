CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    payload TEXT NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    claim_id UUID,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_outbox_pending
    ON notification_outbox (available_at, created_at, id)
    WHERE attempts < 5;
