package com.rideshare.safety.dto;

import com.rideshare.safety.ReportStatus;

/** What the reporter gets back - no details about moderation. */
public record ReportSubmittedResponse(Long id, ReportStatus status) {
}
