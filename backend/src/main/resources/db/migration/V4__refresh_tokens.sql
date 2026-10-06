-- =====================================================================
-- V4: refresh tokens (httpOnly cookie sessions)
-- Short-lived access tokens are kept in browser memory only; a long-lived
-- refresh token lives in an httpOnly cookie. Only a SHA-256 hash of each
-- refresh token is stored. Tokens rotate on every use; all tokens issued from
-- one login share a family_id so that reuse of an old token revokes the family.
-- =====================================================================

CREATE TABLE refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash      VARCHAR(64) NOT NULL,
    family_id       UUID        NOT NULL,
    created_at      TIMESTAMP   NOT NULL,
    expires_at      TIMESTAMP   NOT NULL,
    revoked_at      TIMESTAMP,
    -- ROTATED (normal use), LOGOUT, REUSE_DETECTED
    revoked_reason  VARCHAR(20),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);
