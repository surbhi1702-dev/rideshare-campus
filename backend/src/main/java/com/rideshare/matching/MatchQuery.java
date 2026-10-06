package com.rideshare.matching;

import com.rideshare.matching.geo.GeoPoint;

import java.time.LocalDateTime;

/**
 * "I (requesterId) want to go from source to destination around departureAt
 * with seatsNeeded seats." Built either from an existing ride or from an ad-hoc search.
 *
 * @param excludedRideId a ride that must not appear in the results (e.g. the
 *                       requester's own ride the query was built from), or null
 */
public record MatchQuery(
        Long requesterId,
        GeoPoint source,
        GeoPoint destination,
        LocalDateTime departureAt,
        int seatsNeeded,
        Long excludedRideId
) {

    public MatchQuery {
        if (seatsNeeded < 1) {
            throw new IllegalArgumentException("seatsNeeded must be at least 1");
        }
    }
}
