package com.rideshare.common.exception;

/**
 * Base class for all expected, business-level failures. Each carries an
 * {@link ErrorCode} that decides the HTTP status and the {@code code} field
 * of the error response.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
