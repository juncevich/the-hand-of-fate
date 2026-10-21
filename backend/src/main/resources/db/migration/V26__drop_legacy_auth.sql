-- Keycloak holds credentials, sessions and email verification (docs/adr/0001); the app's own
-- login is gone. Accounts that never confirmed their address go first: their password may have
-- been set by someone else, and nothing could have been created with them.
DELETE FROM users WHERE email_verified_at IS NULL;

-- Verification and reset emails are now sent by Keycloak; queued ones of the app have no reader.
DELETE FROM notification_outbox WHERE event_type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET');

DROP TABLE refresh_tokens;
DROP TABLE email_tokens;

ALTER TABLE users
    DROP COLUMN password_hash,
    DROP COLUMN email_verified_at;
