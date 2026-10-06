-- =====================================================================
-- RideShare Campus - initial schema
-- Times are stored as local institute time (see app.time-zone).
-- =====================================================================

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    email           VARCHAR(255)  NOT NULL,
    password_hash   VARCHAR(100)  NOT NULL,
    phone_number    VARCHAR(20),
    role            VARCHAR(20)   NOT NULL CHECK (role IN ('STUDENT', 'ADMIN')),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP     NOT NULL,
    updated_at      TIMESTAMP     NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE rides (
    id                      BIGSERIAL PRIMARY KEY,
    creator_id              BIGINT         NOT NULL REFERENCES users (id),
    source_name             VARCHAR(120)   NOT NULL,
    source_latitude         DOUBLE PRECISION NOT NULL CHECK (source_latitude BETWEEN -90 AND 90),
    source_longitude        DOUBLE PRECISION NOT NULL CHECK (source_longitude BETWEEN -180 AND 180),
    destination_name        VARCHAR(120)   NOT NULL,
    destination_latitude    DOUBLE PRECISION NOT NULL CHECK (destination_latitude BETWEEN -90 AND 90),
    destination_longitude   DOUBLE PRECISION NOT NULL CHECK (destination_longitude BETWEEN -180 AND 180),
    departure_at            TIMESTAMP      NOT NULL,
    total_seats             INTEGER        NOT NULL CHECK (total_seats > 0),
    occupied_seats          INTEGER        NOT NULL,
    total_fare              NUMERIC(10, 2) NOT NULL CHECK (total_fare >= 0),
    notes                   VARCHAR(500),
    status                  VARCHAR(20)    NOT NULL
                            CHECK (status IN ('OPEN', 'FULL', 'STARTED', 'COMPLETED', 'CANCELLED')),
    created_at              TIMESTAMP      NOT NULL,
    updated_at              TIMESTAMP      NOT NULL,
    -- Last line of defence for seat allocation: the database itself refuses
    -- to store an over-booked ride even if application logic were bypassed.
    CONSTRAINT ck_rides_occupied_range CHECK (occupied_seats >= 0 AND occupied_seats <= total_seats)
);

-- Hot path: matching + browsing always filter "status = OPEN" and a departure window.
CREATE INDEX idx_rides_status_departure ON rides (status, departure_at);
-- Matching narrows candidates with a bounding box on the pickup point.
CREATE INDEX idx_rides_source_coords ON rides (source_latitude, source_longitude);
-- "Rides I created" and admin views.
CREATE INDEX idx_rides_creator ON rides (creator_id);

CREATE TABLE ride_participants (
    id              BIGSERIAL PRIMARY KEY,
    ride_id         BIGINT      NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    user_id         BIGINT      NOT NULL REFERENCES users (id),
    seats_booked    INTEGER     NOT NULL CHECK (seats_booked > 0),
    role            VARCHAR(20) NOT NULL CHECK (role IN ('CREATOR', 'MEMBER')),
    joined_at       TIMESTAMP   NOT NULL,
    -- A student can hold at most one place in a ride (no duplicate joins).
    CONSTRAINT uk_participant_ride_user UNIQUE (ride_id, user_id)
);

-- uk_participant_ride_user already indexes (ride_id, user_id) for "members of ride X".
-- "My rides" looks participants up by user.
CREATE INDEX idx_participants_user ON ride_participants (user_id);

CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    recipient_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type            VARCHAR(40)  NOT NULL,
    message         VARCHAR(500) NOT NULL,
    ride_id         BIGINT       REFERENCES rides (id) ON DELETE SET NULL,
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL
);

-- Inbox listing (newest first) and unread count per user.
CREATE INDEX idx_notifications_recipient_created ON notifications (recipient_id, created_at DESC);
CREATE INDEX idx_notifications_recipient_unread ON notifications (recipient_id) WHERE is_read = FALSE;

CREATE TABLE user_blocks (
    id              BIGSERIAL PRIMARY KEY,
    blocker_id      BIGINT    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id      BIGINT    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at      TIMESTAMP NOT NULL,
    CONSTRAINT uk_user_blocks_pair UNIQUE (blocker_id, blocked_id),
    CONSTRAINT ck_user_blocks_not_self CHECK (blocker_id <> blocked_id)
);

CREATE INDEX idx_user_blocks_blocked ON user_blocks (blocked_id);

CREATE TABLE reports (
    id                  BIGSERIAL PRIMARY KEY,
    reporter_id         BIGINT       NOT NULL REFERENCES users (id),
    reported_user_id    BIGINT       NOT NULL REFERENCES users (id),
    ride_id             BIGINT       REFERENCES rides (id) ON DELETE SET NULL,
    reason              VARCHAR(30)  NOT NULL,
    description         VARCHAR(1000),
    status              VARCHAR(20)  NOT NULL CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    created_at          TIMESTAMP    NOT NULL,
    resolved_at         TIMESTAMP,
    CONSTRAINT ck_reports_not_self CHECK (reporter_id <> reported_user_id)
);

CREATE INDEX idx_reports_status_created ON reports (status, created_at DESC);
