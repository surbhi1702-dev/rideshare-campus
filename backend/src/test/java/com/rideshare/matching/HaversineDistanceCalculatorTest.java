package com.rideshare.matching;

import com.rideshare.matching.geo.BoundingBox;
import com.rideshare.matching.geo.GeoPoint;
import com.rideshare.matching.geo.HaversineDistanceCalculator;
import org.junit.jupiter.api.Test;

import static com.rideshare.support.TestRides.AIRPORT;
import static com.rideshare.support.TestRides.CAMPUS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class HaversineDistanceCalculatorTest {

    private final HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();

    @Test
    void samePointIsZeroKm() {
        assertThat(calculator.distanceKm(CAMPUS, CAMPUS)).isEqualTo(0.0);
    }

    @Test
    void londonToParisMatchesKnownGreatCircleDistance() {
        double km = calculator.distanceKm(new GeoPoint(51.5074, -0.1278), new GeoPoint(48.8566, 2.3522));
        assertThat(km).isCloseTo(343.56, within(0.1));
    }

    @Test
    void campusToAirportIsAboutNineteenKmStraightLine() {
        // Road distance is noticeably longer - Haversine is only the straight line.
        assertThat(calculator.distanceKm(CAMPUS, AIRPORT)).isCloseTo(18.71, within(0.05));
    }

    @Test
    void distanceIsSymmetric() {
        assertThat(calculator.distanceKm(CAMPUS, AIRPORT)).isEqualTo(calculator.distanceKm(AIRPORT, CAMPUS));
    }

    @Test
    void invalidCoordinatesAreRejected() {
        assertThatThrownBy(() -> new GeoPoint(91, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(0, 181)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void boundingBoxContainsEveryPointOnTheCircle() {
        double radiusKm = 3.0;
        BoundingBox box = BoundingBox.around(CAMPUS, radiusKm);
        for (int bearing = 0; bearing < 360; bearing += 15) {
            GeoPoint onCircle = destinationPoint(CAMPUS, radiusKm, bearing);
            assertThat(calculator.distanceKm(CAMPUS, onCircle)).isCloseTo(radiusKm, within(0.01));
            assertThat(onCircle.latitude()).isBetween(box.minLatitude(), box.maxLatitude());
            assertThat(onCircle.longitude()).isBetween(box.minLongitude(), box.maxLongitude());
        }
    }

    /** Standard "destination point given distance and bearing" formula. */
    private static GeoPoint destinationPoint(GeoPoint start, double km, double bearingDegrees) {
        double r = 6371.0088;
        double delta = km / r;
        double theta = Math.toRadians(bearingDegrees);
        double lat1 = Math.toRadians(start.latitude());
        double lon1 = Math.toRadians(start.longitude());
        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(delta) + Math.cos(lat1) * Math.sin(delta) * Math.cos(theta));
        double lon2 = lon1 + Math.atan2(Math.sin(theta) * Math.sin(delta) * Math.cos(lat1),
                Math.cos(delta) - Math.sin(lat1) * Math.sin(lat2));
        return new GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2));
    }
}
