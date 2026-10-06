package com.rideshare.ride;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Human readable fragments used in notification texts. */
public final class RideMessages {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.ENGLISH);

    private RideMessages() {
    }

    /** e.g. "IIT Bhubaneswar -> Airport (05 Oct, 09:30)" */
    public static String describe(Ride ride) {
        return "%s -> %s (%s)".formatted(ride.getSourceName(), ride.getDestinationName(),
                ride.getDepartureAt().format(WHEN));
    }

    public static String seats(Ride ride) {
        return "%d/%d seats taken".formatted(ride.getOccupiedSeats(), ride.getTotalSeats());
    }
}
