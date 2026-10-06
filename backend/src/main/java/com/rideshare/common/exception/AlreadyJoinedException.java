package com.rideshare.common.exception;

/** The student already holds a place in this ride (409). */
public class AlreadyJoinedException extends ApiException {

    public AlreadyJoinedException() {
        super(ErrorCode.ALREADY_JOINED, "You are already part of this ride");
    }
}
