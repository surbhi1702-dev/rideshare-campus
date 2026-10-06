package com.rideshare.ride.dto;

import com.rideshare.ride.ParticipantRole;

import java.math.BigDecimal;
import java.util.List;

/**
 * Detail view. Members get participant names, phone numbers and the fare split;
 * non-members only see the ride, the creator's display name, a member count and
 * what their share would be if they joined.
 */
public record RideDetailResponse(
        RideSummaryResponse ride,
        String notes,
        ParticipantRole viewerRole,
        int participantCount,
        List<ParticipantResponse> participants,
        FareSplitResponse fareSplit,
        BigDecimal estimatedShareIfJoined,
        RideActions actions
) {
}
