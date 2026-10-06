package com.rideshare.admin.dto;

import com.rideshare.ride.dto.RideSummaryResponse;

/** Ride summary plus the creator's contact email for moderation. */
public record AdminRideResponse(RideSummaryResponse ride, String creatorEmail) {
}
