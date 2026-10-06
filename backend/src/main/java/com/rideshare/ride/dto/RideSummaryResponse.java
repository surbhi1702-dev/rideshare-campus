package com.rideshare.ride.dto;

import com.rideshare.ride.RideStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RideSummaryResponse(
        Long id,
        String sourceName,
        double sourceLatitude,
        double sourceLongitude,
        String destinationName,
        double destinationLatitude,
        double destinationLongitude,
        LocalDateTime departureAt,
        int totalSeats,
        int occupiedSeats,
        int availableSeats,
        BigDecimal totalFare,
        BigDecimal currentSharePerSeat,
        RideStatus status,
        PublicUserSummary creator
) {
}
