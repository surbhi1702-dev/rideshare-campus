package com.rideshare.matching.geo;

/**
 * Axis-aligned lat/lng rectangle that fully contains a circle of the given radius.
 * Used to let the database discard far-away rides with a cheap, indexable range
 * check before the exact Haversine distance is computed in Java.
 */
public record BoundingBox(double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {

   // private static final double KM_PER_DEGREE_LATITUDE = 111.32;
    private static final double KM_PER_DEGREE_LATITUDE = 111.0;
    /** Avoids division by ~0 near the poles (irrelevant for India, but keeps the maths total). */
    private static final double MIN_COS_LATITUDE = 0.01;

    public static BoundingBox around(GeoPoint center, double radiusKm) {
        double latDelta = radiusKm / KM_PER_DEGREE_LATITUDE;
        double cosLat = Math.max(Math.cos(Math.toRadians(center.latitude())), MIN_COS_LATITUDE);
        double lngDelta = radiusKm / (KM_PER_DEGREE_LATITUDE * cosLat);
        return new BoundingBox(
                Math.max(-90, center.latitude() - latDelta),
                Math.min(90, center.latitude() + latDelta),
                Math.max(-180, center.longitude() - lngDelta),
                Math.min(180, center.longitude() + lngDelta));
    }
}
