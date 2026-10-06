package com.rideshare.ride.dto;

import com.rideshare.ride.ParticipantRole;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Full member details; returned only to members of the same ride. */
public record ParticipantResponse(
        Long userId,
        String name,
        String phoneNumber,
        int seatsBooked,
        ParticipantRole role,
        LocalDateTime joinedAt,
        BigDecimal estimatedShare
) {
}
