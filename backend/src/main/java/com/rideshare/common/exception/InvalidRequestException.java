package com.rideshare.common.exception;

/** Input is syntactically valid but breaks a business rule (400). */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String message) {
        super(ErrorCode.INVALID_REQUEST, message);
    }

    public InvalidRequestException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
