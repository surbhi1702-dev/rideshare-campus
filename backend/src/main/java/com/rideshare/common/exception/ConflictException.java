package com.rideshare.common.exception;

/** The request conflicts with the current state of a resource (409). */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(ErrorCode.INVALID_STATE_TRANSITION, message);
    }

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
