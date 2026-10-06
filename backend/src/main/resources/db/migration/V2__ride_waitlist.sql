-- =====================================================================
-- V2: waitlist for full rides
-- Students queue for a full ride; when seats free up, waiters are promoted
-- automatically (first-fit in arrival order) inside the same locked
-- transaction that released the seats.
-- =====================================================================

CREATE TABLE ride_waitlist (
    id                  BIGSERIAL PRIMARY KEY,
    ride_id             BIGINT    NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    user_id             BIGINT    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    seats_requested     INTEGER   NOT NULL CHECK (seats_requested > 0),
    created_at          TIMESTAMP NOT NULL,
    -- A student can queue for a given ride only once.
    CONSTRAINT uk_waitlist_ride_user UNIQUE (ride_id, user_id)
);

-- Reading one ride's queue in arrival (FIFO) order during promotion.
CREATE INDEX idx_waitlist_ride_created ON ride_waitlist (ride_id, created_at, id);
-- "Which queues am I in".
CREATE INDEX idx_waitlist_user ON ride_waitlist (user_id);
