package com.rideshare.ride.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * @param seats         seats to book (default 1)
 * @param replaceRideId optional: your own ride (with no other members) to cancel
 *                      atomically as part of this join - "merge my trip into theirs"
 */
public record JoinRideRequest(
        @Schema(example = "1") @Min(1) @Max(9) Integer seats,
        @Schema(description = "Your own ride to cancel in the same transaction", nullable = true) Long replaceRideId
) {

    public int seatsOrDefault() {
        return seats == null ? 1 : seats;
    }
}
