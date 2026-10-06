package com.rideshare.matching.geo;

import org.springframework.stereotype.Component;

/**
 * Great-circle ("as the crow flies") distance using the Haversine formula.
 *
 * <p>IMPORTANT: this is geographical distance, NOT driving distance. Roads are
 * longer than the straight line (typically 1.2x-1.5x in a city), and a river or
 * a highway without a crossing can make two "close" points far apart by road.
 * It is used here only to decide whether two pickup points (or two drop points)
 * are near each other, where a straight-line radius of a few km is a reasonable
 * and free approximation.</p>
 */
@Component
public class HaversineDistanceCalculator implements DistanceCalculator {

    /** Mean Earth radius (IUGG), km. */
    static final double EARTH_RADIUS_KM = 6371.0088;

    @Override
    public double distanceKm(GeoPoint from, GeoPoint to) {
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(to.longitude() - from.longitude());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        // min() guards against floating point drift slightly above 1 for antipodal points.
        double c = 2 * Math.asin(Math.min(1.0, Math.sqrt(a)));
        return EARTH_RADIUS_KM * c;
    }
}
