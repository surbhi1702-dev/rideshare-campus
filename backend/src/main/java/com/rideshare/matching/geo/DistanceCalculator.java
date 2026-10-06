package com.rideshare.matching.geo;

/**
 * Distance between two points in kilometres. The production implementation is
 * straight-line (great-circle) distance; a road-distance implementation backed by
 * a routing engine could be plugged in later without touching the matching engine.
 */
public interface DistanceCalculator {

    double distanceKm(GeoPoint from, GeoPoint to);
}
