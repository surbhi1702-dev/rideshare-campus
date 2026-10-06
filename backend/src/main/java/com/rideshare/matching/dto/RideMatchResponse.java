package com.rideshare.matching.dto;

import com.rideshare.matching.MatchScore;
import com.rideshare.ride.dto.RideSummaryResponse;

import java.math.BigDecimal;

/**
 * One ranked match. {@code match.score} is in [0, 1] (lower is better) and
 * {@code match.compatibilityPercent} is the same thing as a friendly 0-100 value.
 * Distances are straight-line, not road distances.
 */
public record RideMatchResponse(
        RideSummaryResponse ride,
        MatchScore match,
        BigDecimal estimatedShareIfJoined
) {
}
