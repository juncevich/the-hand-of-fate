-- Jobs that exhaust app.notifications.max-attempts are marked failed (dead-letter) instead of
-- relying on a hard-coded attempts limit; they are purged after app.notifications.failed-retention.
ALTER TABLE notification_outbox ADD COLUMN failed_at TIMESTAMPTZ;

-- Jobs already past the previous fixed limit of 5 attempts become failed records.
UPDATE notification_outbox SET failed_at = now() WHERE attempts >= 5;

DROP INDEX idx_notification_outbox_pending;

-- Matches the claim query: pending jobs in FIFO order.
CREATE INDEX idx_notification_outbox_pending
    ON notification_outbox (created_at, id)
    WHERE failed_at IS NULL;

-- Retention purge of failed jobs.
CREATE INDEX idx_notification_outbox_failed
    ON notification_outbox (failed_at)
    WHERE failed_at IS NOT NULL;
