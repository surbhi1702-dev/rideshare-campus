package com.rideshare.common.exception;

/** The caller is authenticated but not allowed to do this (403). */
public class ForbiddenOperationException extends ApiException {

    public ForbiddenOperationException(String message) {
        super(ErrorCode.ACCESS_DENIED, message);
    }

    public ForbiddenOperationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
