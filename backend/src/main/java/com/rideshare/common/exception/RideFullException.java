package com.rideshare.common.exception;

/** Not enough free seats left in the ride (409). */
public class RideFullException extends ApiException {

    public RideFullException(int requestedSeats, int availableSeats) {
        super(ErrorCode.RIDE_FULL, availableSeats == 0
                ? "This ride is already full"
                : "Only %d seat(s) left, %d requested".formatted(availableSeats, requestedSeats));
    }
}
