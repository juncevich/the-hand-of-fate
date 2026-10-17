-- Addresses that unsubscribed through the link in a notification email (with or without an account).
-- Notification emails (invitations, draw results) are not delivered to them; account emails
-- (verification, password reset) still are.
CREATE TABLE email_suppressions (
    email      VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
