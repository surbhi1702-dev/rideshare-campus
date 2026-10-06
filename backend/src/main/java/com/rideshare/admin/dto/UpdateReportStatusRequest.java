package com.rideshare.admin.dto;

import com.rideshare.safety.ReportStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateReportStatusRequest(@NotNull ReportStatus status) {
}
