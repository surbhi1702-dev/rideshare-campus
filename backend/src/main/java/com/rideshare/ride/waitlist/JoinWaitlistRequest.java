package com.rideshare.ride.waitlist;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** @param seats seats wanted once they free up (default 1) */
public record JoinWaitlistRequest(@Schema(example = "1") @Min(1) @Max(9) Integer seats) {

    public int seatsOrDefault() {
        return seats == null ? 1 : seats;
    }
}
