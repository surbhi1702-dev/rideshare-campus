package com.rideshare.admin.dto;

import com.rideshare.ride.RideStatus;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @param estimatedSavingsOnCompletedRides sum over completed rides of (riders - 1) x fare:
 *                                         what riders would have paid extra travelling alone.
 *                                         An estimate, not accounting data.
 */
public record AdminStatsResponse(
        long totalUsers,
        long activeUsers,
        long totalRides,
        Map<RideStatus, Long> ridesByStatus,
        long upcomingActiveRides,
        long openReports,
        BigDecimal estimatedSavingsOnCompletedRides
) {
}
