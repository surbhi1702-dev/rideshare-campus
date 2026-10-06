package com.rideshare.ride.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** Simple list filters for browsing open rides (text + date/time + seats). */
public record RideBrowseFilter(
        String source,
        String destination,
        LocalDate date,
        LocalTime fromTime,
        LocalTime toTime,
        Integer minSeats
) {
}
