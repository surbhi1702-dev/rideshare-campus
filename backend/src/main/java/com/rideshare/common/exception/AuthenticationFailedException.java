package com.rideshare.common.exception;

/** Credentials were missing or wrong (401). */
public class AuthenticationFailedException extends ApiException {

    public AuthenticationFailedException(String message) {
        super(ErrorCode.INVALID_CREDENTIALS, message);
    }

    public AuthenticationFailedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
