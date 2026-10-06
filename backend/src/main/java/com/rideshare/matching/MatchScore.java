package com.rideshare.matching;

/**
 * Explainable result of scoring one candidate ride.
 *
 * @param pickupDistanceKm      straight-line distance between pickup points (not road distance)
 * @param destinationDistanceKm straight-line distance between drop points (not road distance)
 * @param timeDifferenceMinutes absolute difference between desired and ride departure
 * @param pickupComponent       pickupDistanceKm / maxPickupDistanceKm, in [0, 1]
 * @param destinationComponent  destinationDistanceKm / maxDestinationDistanceKm, in [0, 1]
 * @param timeComponent         timeDifferenceMinutes / maxTimeDifferenceMinutes, in [0, 1]
 * @param groupComponent        share of seats still empty after joining, in [0, 1)
 * @param score                 weighted average of the components, in [0, 1]; LOWER is better
 * @param compatibilityPercent  round(100 x (1 - score)), for display
 */
public record MatchScore(
        double pickupDistanceKm,
        double destinationDistanceKm,
        long timeDifferenceMinutes,
        double pickupComponent,
        double destinationComponent,
        double timeComponent,
        double groupComponent,
        double score,
        int compatibilityPercent
) {
}
