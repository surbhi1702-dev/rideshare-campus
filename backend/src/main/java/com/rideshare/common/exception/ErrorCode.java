package com.rideshare.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine readable error codes returned in every error response.
 * The frontend switches on these, never on message text.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_DOMAIN(HttpStatus.BAD_REQUEST),
    INVALID_DEPARTURE_TIME(HttpStatus.BAD_REQUEST),
    INVALID_SEAT_COUNT(HttpStatus.BAD_REQUEST),
    INVALID_ROUTE(HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST),
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST),

    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED),

    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),
    INTERACTION_BLOCKED(HttpStatus.FORBIDDEN),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    RIDE_NOT_FOUND(HttpStatus.NOT_FOUND),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),

    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT),
    RIDE_FULL(HttpStatus.CONFLICT),
    ALREADY_JOINED(HttpStatus.CONFLICT),
    RIDE_CANCELLED(HttpStatus.CONFLICT),
    RIDE_COMPLETED(HttpStatus.CONFLICT),
    RIDE_ALREADY_STARTED(HttpStatus.CONFLICT),
    RIDE_ALREADY_DEPARTED(HttpStatus.CONFLICT),
    NOT_A_PARTICIPANT(HttpStatus.CONFLICT),
    CREATOR_CANNOT_LEAVE(HttpStatus.CONFLICT),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT),
    OVERLAPPING_RIDE(HttpStatus.CONFLICT),
    ALREADY_BLOCKED(HttpStatus.CONFLICT),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT),
    ALREADY_WAITLISTED(HttpStatus.CONFLICT),
    NOT_WAITLISTED(HttpStatus.CONFLICT),
    SEATS_AVAILABLE(HttpStatus.CONFLICT),
    WAITLIST_FULL(HttpStatus.CONFLICT),

    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
