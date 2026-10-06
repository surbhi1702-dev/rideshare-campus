package com.rideshare.matching;

import com.rideshare.ride.Ride;

public record MatchResult(Ride ride, MatchScore score) {
}
