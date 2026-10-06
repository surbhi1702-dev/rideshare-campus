-- =====================================================================
-- V3: idempotency keys for join / waitlist requests
-- The client sends an Idempotency-Key header; a retried request with the same
-- key (e.g. after a network timeout) is answered from the current ride state
-- instead of booking twice. Keys are purged after 24 hours.
-- =====================================================================

CREATE TABLE idempotency_keys (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    idem_key        VARCHAR(100) NOT NULL,
    operation       VARCHAR(30)  NOT NULL,
    ride_id         BIGINT       NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    created_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uk_idempotency_user_key UNIQUE (user_id, idem_key)
);

-- Purge job deletes by age.
CREATE INDEX idx_idempotency_created ON idempotency_keys (created_at);
