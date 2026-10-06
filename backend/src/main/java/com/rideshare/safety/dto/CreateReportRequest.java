package com.rideshare.safety.dto;

import com.rideshare.safety.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotNull Long reportedUserId,
        Long rideId,
        @NotNull ReportReason reason,
        @Size(max = 1000) String description
) {
}
