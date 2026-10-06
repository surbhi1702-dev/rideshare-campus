package com.rideshare.common.idempotency;

/** Requests that accept an Idempotency-Key header. */
public enum IdempotentOperation {
    JOIN_RIDE,
    JOIN_WAITLIST
}
