-- Keycloak becomes the authorization server (docs/adr/0001): users.id equals the Keycloak subject.
-- An account that existed before Keycloak is taken over on its first Keycloak sign-in with the same
-- verified email by changing its id to the subject, so every reference to users(id) follows it.
ALTER TABLE votes
    DROP CONSTRAINT votes_creator_id_fkey,
    ADD CONSTRAINT votes_creator_id_fkey
        FOREIGN KEY (creator_id) REFERENCES users (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE refresh_tokens
    DROP CONSTRAINT refresh_tokens_user_id_fkey,
    ADD CONSTRAINT refresh_tokens_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE telegram_link_tokens
    DROP CONSTRAINT telegram_link_tokens_user_id_fkey,
    ADD CONSTRAINT telegram_link_tokens_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE email_budget_ledger
    DROP CONSTRAINT email_budget_ledger_user_id_fkey,
    ADD CONSTRAINT email_budget_ledger_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE email_tokens
    DROP CONSTRAINT email_tokens_user_id_fkey,
    ADD CONSTRAINT email_tokens_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE ON UPDATE CASCADE;

-- Deleted accounts, kept for a while (app.keycloak.deleted-account-retention):
--  * an access token issued before the deletion stays valid until it expires; this row stops it
--    from provisioning the account again;
--  * the Keycloak user is deleted after the local commit by a retrying job — identity_deleted_at
--    is NULL until Keycloak confirmed it.
CREATE TABLE deleted_accounts (
    user_id             UUID        PRIMARY KEY,
    deleted_at          TIMESTAMPTZ NOT NULL,
    identity_deleted_at TIMESTAMPTZ
);

CREATE INDEX deleted_accounts_identity_pending_idx
    ON deleted_accounts (deleted_at) WHERE identity_deleted_at IS NULL;
