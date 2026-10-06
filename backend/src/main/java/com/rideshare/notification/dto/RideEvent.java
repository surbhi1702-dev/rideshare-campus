package com.rideshare.notification.dto;

import com.rideshare.ride.RideStatus;

import java.time.Instant;

/**
 * Real-time message broadcast on /topic/rides/{rideId}. It deliberately carries
 * no personal data (no names or phone numbers): clients use it as a signal to
 * refresh, and the refresh goes through the normal authorised REST endpoint.
 */
public record RideEvent(
        Long rideId,
        RideEventType type,
        RideStatus status,
        int occupiedSeats,
        int totalSeats,
        int availableSeats,
        Instant occurredAt
) {

    public enum RideEventType {
        PARTICIPANT_JOINED,
        PARTICIPANT_LEFT,
        RIDE_UPDATED,
        RIDE_CANCELLED,
        RIDE_STARTED,
        RIDE_COMPLETED
    }
}
