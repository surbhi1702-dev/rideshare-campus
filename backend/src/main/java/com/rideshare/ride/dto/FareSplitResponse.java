package com.rideshare.ride.dto;

import java.math.BigDecimal;

/**
 * Estimated split of the expected fare among the current seats. Shares add up to
 * the total exactly (leftover paise go to the earliest joiners). No payment is
 * processed by the platform.
 */
public record FareSplitResponse(
        BigDecimal totalFare,
        int occupiedSeats,
        BigDecimal sharePerSeat,
        BigDecimal yourShare
) {
}
