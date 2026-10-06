package com.rideshare.ride;

import com.rideshare.common.exception.ErrorCode;
import com.rideshare.common.exception.InvalidRequestException;
import com.rideshare.config.AppProperties;
import com.rideshare.matching.geo.DistanceCalculator;
import com.rideshare.matching.geo.GeoPoint;
import com.rideshare.ride.dto.CreateRideRequest;
import com.rideshare.ride.dto.UpdateRideRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Business validation of ride input that bean validation cannot express:
 * time must be in the future, capacity within configured limits, a real route.
 */
@Component
public class RideValidator {

    private final Clock clock;
    private final AppProperties.Ride rules;
    private final DistanceCalculator distanceCalculator;

    public RideValidator(Clock clock, AppProperties properties, DistanceCalculator distanceCalculator) {
        this.clock = clock;
        this.rules = properties.ride();
        this.distanceCalculator = distanceCalculator;
    }

    public RideDetails validateCreate(CreateRideRequest request) {
        int creatorSeats = request.seatsForCreator() == null ? 1 : request.seatsForCreator();
        if (creatorSeats >= request.totalSeats()) {
            throw new InvalidRequestException(ErrorCode.INVALID_SEAT_COUNT,
                    "Leave at least one seat free for others (you need %d of %d)".formatted(creatorSeats,
                            request.totalSeats()));
        }
        return buildDetails(request.sourceName(), request.sourceLatitude(), request.sourceLongitude(),
                request.destinationName(), request.destinationLatitude(), request.destinationLongitude(),
                request.departureDate(), request.departureTime(), request.totalSeats(), request.totalFare(),
                request.notes());
    }

    public RideDetails validateUpdate(UpdateRideRequest request, Ride existing) {
        if (request.totalSeats() < existing.getOccupiedSeats()) {
            throw new InvalidRequestException(ErrorCode.INVALID_SEAT_COUNT,
                    "%d seats are already taken; capacity cannot go below that".formatted(existing.getOccupiedSeats()));
        }
        return buildDetails(request.sourceName(), request.sourceLatitude(), request.sourceLongitude(),
                request.destinationName(), request.destinationLatitude(), request.destinationLongitude(),
                request.departureDate(), request.departureTime(), request.totalSeats(), request.totalFare(),
                request.notes());
    }

    public LocalDateTime requireValidDeparture(LocalDate date, LocalTime time) {
        LocalDateTime departureAt = LocalDateTime.of(date, time.withSecond(0).withNano(0));
        LocalDateTime now = LocalDateTime.now(clock);
        if (!departureAt.isAfter(now)) {
            throw new InvalidRequestException(ErrorCode.INVALID_DEPARTURE_TIME, "Departure time must be in the future");
        }
        if (departureAt.isAfter(now.plusDays(rules.maxDaysAhead()))) {
            throw new InvalidRequestException(ErrorCode.INVALID_DEPARTURE_TIME,
                    "Rides can be planned at most %d days ahead".formatted(rules.maxDaysAhead()));
        }
        return departureAt;
    }

    private RideDetails buildDetails(String sourceName, double sourceLat, double sourceLng,
                                     String destinationName, double destinationLat, double destinationLng,
                                     LocalDate date, LocalTime time, int totalSeats, BigDecimal totalFare,
                                     String notes) {
        LocalDateTime departureAt = requireValidDeparture(date, time);
        if (totalSeats > rules.maxSeats()) {
            throw new InvalidRequestException(ErrorCode.INVALID_SEAT_COUNT,
                    "A ride can have at most %d seats".formatted(rules.maxSeats()));
        }
        GeoPoint source = new GeoPoint(sourceLat, sourceLng);
        GeoPoint destination = new GeoPoint(destinationLat, destinationLng);
        if (distanceCalculator.distanceKm(source, destination) < rules.minTripDistanceKm()) {
            throw new InvalidRequestException(ErrorCode.INVALID_ROUTE,
                    "Source and destination are too close to each other");
        }
        String cleanNotes = notes == null || notes.isBlank() ? null : notes.trim();
        return new RideDetails(sourceName.trim(), source, destinationName.trim(), destination, departureAt,
                totalSeats, totalFare.setScale(2, java.math.RoundingMode.HALF_UP), cleanNotes);
    }
}
