package com.rideshare.matching;

import com.rideshare.matching.geo.GeoPoint;
import com.rideshare.matching.geo.HaversineDistanceCalculator;
import com.rideshare.ride.Ride;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.rideshare.support.TestRides.AIRPORT;
import static com.rideshare.support.TestRides.CAMPUS;
import static com.rideshare.support.TestRides.CUTTACK;
import static com.rideshare.support.TestRides.RAILWAY_STATION;
import static com.rideshare.support.TestRides.ride;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MatchScorerTest {

    static final MatchingProperties PROPS = new MatchingProperties(3.0, 5.0, 60, 20, 10,
            new MatchingProperties.Weights(0.30, 0.35, 0.25, 0.10));
    static final LocalDateTime NINE_AM = LocalDateTime.of(2030, 1, 10, 9, 0);

    private final MatchScorer scorer = new MatchScorer(new HaversineDistanceCalculator(), PROPS);

    private static MatchQuery query(GeoPoint from, GeoPoint to, LocalDateTime when, int seats) {
        return new MatchQuery(1L, from, to, when, seats, null);
    }

    @Test
    void identicalTripOnlyPaysTheGroupComponent() {
        Ride ride = ride(1, CAMPUS, AIRPORT, NINE_AM, 4, 1);

        MatchScore score = scorer.score(query(CAMPUS, AIRPORT, NINE_AM, 1), ride).orElseThrow();

        assertThat(score.pickupDistanceKm()).isZero();
        assertThat(score.destinationDistanceKm()).isZero();
        assertThat(score.timeDifferenceMinutes()).isZero();
        // After joining 2 of 4 seats are taken -> g = 0.5, weighted by 0.10 / 1.0
        assertThat(score.groupComponent()).isEqualTo(0.5);
        assertThat(score.score()).isCloseTo(0.05, within(1e-9));
        assertThat(score.compatibilityPercent()).isEqualTo(95);
    }

    @Test
    void componentsAreNormalisedByTheirOwnTolerance() {
        // 30 minutes apart = half of the 60 minute tolerance -> t = 0.5
        Ride ride = ride(1, CAMPUS, AIRPORT, NINE_AM.plusMinutes(30), 4, 3);

        MatchScore score = scorer.score(query(CAMPUS, AIRPORT, NINE_AM, 1), ride).orElseThrow();

        assertThat(score.timeComponent()).isEqualTo(0.5);
        assertThat(score.groupComponent()).isEqualTo(0.0); // joining fills the car
        assertThat(score.score()).isCloseTo(0.25 * 0.5, within(1e-9));
    }

    @Test
    void pickupBeyondMaximumIsRejected() {
        Ride fromStation = ride(1, RAILWAY_STATION, AIRPORT, NINE_AM, 4, 1);
        assertThat(scorer.score(query(CAMPUS, AIRPORT, NINE_AM, 1), fromStation)).isEmpty();
    }

    @Test
    void destinationBeyondMaximumIsRejected() {
        Ride toCuttack = ride(1, CAMPUS, CUTTACK, NINE_AM, 4, 1);
        assertThat(scorer.score(query(CAMPUS, AIRPORT, NINE_AM, 1), toCuttack)).isEmpty();
    }

    @Test
    void destinationWithinToleranceIsAcceptedButPenalised() {
        // Airport and railway station are ~3.6 km apart: inside the 5 km tolerance.
        Ride toStation = ride(1, CAMPUS, RAILWAY_STATION, NINE_AM, 4, 1);

        MatchScore score = scorer.score(query(CAMPUS, AIRPORT, NINE_AM, 1), toStation).orElseThrow();

        assertThat(score.destinationDistanceKm()).isCloseTo(3.64, within(0.01));
        assertThat(score.destinationComponent()).isCloseTo(3.64 / 5.0, within(0.01));
    }

    @Test
    void timeDifferenceIsSymmetricAndCappedByTolerance() {
        assertThat(scorer.score(query(CAMPUS, AIRPORT, NINE_AM, 1), ride(1, CAMPUS, AIRPORT, NINE_AM.minusMinutes(60), 4, 1)))
                .isPresent();
        assertThat(scorer.score(query(CAMPUS, AIRPORT, NINE_AM, 1), ride(2, CAMPUS, AIRPORT, NINE_AM.plusMinutes(61), 4, 1)))
                .isEmpty();
        assertThat(scorer.score(query(CAMPUS, AIRPORT, NINE_AM, 1), ride(3, CAMPUS, AIRPORT, NINE_AM.minusMinutes(61), 4, 1)))
                .isEmpty();
    }

    @Test
    void notEnoughSeatsIsRejected() {
        Ride oneSeatLeft = ride(1, CAMPUS, AIRPORT, NINE_AM, 4, 3);
        assertThat(scorer.score(query(CAMPUS, AIRPORT, NINE_AM, 2), oneSeatLeft)).isEmpty();
        assertThat(scorer.score(query(CAMPUS, AIRPORT, NINE_AM, 1), oneSeatLeft)).isPresent();
    }

    @Test
    void closerRideScoresBetterThanLaterRide() {
        Optional<MatchScore> sameTime = scorer.score(query(CAMPUS, AIRPORT, NINE_AM, 1),
                ride(1, CAMPUS, AIRPORT, NINE_AM, 4, 1));
        Optional<MatchScore> later = scorer.score(query(CAMPUS, AIRPORT, NINE_AM, 1),
                ride(2, CAMPUS, AIRPORT, NINE_AM.plusMinutes(45), 4, 1));

        assertThat(sameTime.orElseThrow().score()).isLessThan(later.orElseThrow().score());
    }

    @Test
    void scoreStaysWithinZeroAndOne() {
        // Worst acceptable case on every axis.
        GeoPoint pickupNearLimit = new GeoPoint(CAMPUS.latitude() + 0.0265, CAMPUS.longitude()); // ~2.95 km
        Ride worst = ride(1, pickupNearLimit, RAILWAY_STATION, NINE_AM.plusMinutes(60), 7, 1);

        MatchScore score = scorer.score(query(CAMPUS, AIRPORT, NINE_AM, 1), worst).orElseThrow();

        assertThat(score.score()).isBetween(0.0, 1.0);
        assertThat(score.compatibilityPercent()).isBetween(0, 100);
    }
}
