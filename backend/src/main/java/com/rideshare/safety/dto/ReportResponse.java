package com.rideshare.safety.dto;

import com.rideshare.safety.ReportReason;
import com.rideshare.safety.ReportStatus;

import java.time.LocalDateTime;

/** Admin view of a report. */
public record ReportResponse(
        Long id,
        Long reporterId,
        String reporterEmail,
        Long reportedUserId,
        String reportedUserEmail,
        boolean reportedUserActive,
        Long rideId,
        ReportReason reason,
        String description,
        ReportStatus status,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt
) {
}
