package com.rideshare.matching;

import com.rideshare.matching.geo.DistanceCalculator;
import com.rideshare.ride.Ride;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Stages 2 and 3 of matching for a single candidate: hard compatibility filters,
 * then a normalised weighted cost.
 *
 * <pre>
 *   p = pickupKm      / MAX_PICKUP_DISTANCE_KM            (0 = same spot, 1 = at the limit)
 *   d = destinationKm / MAX_DESTINATION_DISTANCE_KM
 *   t = |deltaMinutes| / MAX_TIME_DIFFERENCE_MINUTES
 *   g = 1 - (occupiedSeats + seatsNeeded) / totalSeats     (0 = joining fills the car)
 *
 *   score = (wp*p + wd*d + wt*t + wg*g) / (wp + wd + wt + wg)       lower is better
 * </pre>
 *
 * Dividing each raw value by its own tolerance turns kilometres and minutes into
 * the same dimensionless "fraction of what the student is willing to accept", so
 * no unit can dominate just because its numbers are bigger. Anything beyond a
 * tolerance is rejected outright instead of being scored.
 */
@Component
public class MatchScorer {

    private final DistanceCalculator distanceCalculator;
    private final MatchingProperties properties;

    public MatchScorer(DistanceCalculator distanceCalculator, MatchingProperties properties) {
        this.distanceCalculator = distanceCalculator;
        this.properties = properties;
    }

    /** @return the score, or empty if the ride is incompatible with the query. */
    public Optional<MatchScore> score(MatchQuery query, Ride ride) {
        if (ride.getAvailableSeats() < query.seatsNeeded()) {
            return Optional.empty();
        }

        double pickupKm = distanceCalculator.distanceKm(query.source(), ride.getSource());
        if (pickupKm > properties.maxPickupDistanceKm()) {
            return Optional.empty();
        }

        double destinationKm = distanceCalculator.distanceKm(query.destination(), ride.getDestination());
        if (destinationKm > properties.maxDestinationDistanceKm()) {
            return Optional.empty();
        }

        long minutesApart = Math.abs(Duration.between(query.departureAt(), ride.getDepartureAt()).toMinutes());
        if (minutesApart > properties.maxTimeDifferenceMinutes()) {
            return Optional.empty();
        }

        double pickup = pickupKm / properties.maxPickupDistanceKm();
        double destination = destinationKm / properties.maxDestinationDistanceKm();
        double time = (double) minutesApart / properties.maxTimeDifferenceMinutes();
        double group = 1.0 - (double) (ride.getOccupiedSeats() + query.seatsNeeded()) / ride.getTotalSeats();

        MatchingProperties.Weights w = properties.weights();
        double score = (w.pickup() * pickup + w.destination() * destination + w.time() * time + w.group() * group)
                / w.sum();
        score = clamp(score);
        int compatibility = (int) Math.round(100 * (1 - score));

        return Optional.of(new MatchScore(round(pickupKm, 2), round(destinationKm, 2), minutesApart,
                round(pickup, 4), round(destination, 4), round(time, 4), round(group, 4), round(score, 4),
                compatibility));
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
