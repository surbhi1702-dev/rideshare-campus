package com.rideshare.ride;

import com.rideshare.matching.geo.GeoPoint;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Validated, normalised ride attributes shared by create and update. */
public record RideDetails(
        String sourceName,
        GeoPoint source,
        String destinationName,
        GeoPoint destination,
        LocalDateTime departureAt,
        int totalSeats,
        BigDecimal totalFare,
        String notes
) {
}
