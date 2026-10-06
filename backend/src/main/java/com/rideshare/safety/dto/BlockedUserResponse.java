package com.rideshare.safety.dto;

import java.time.LocalDateTime;

public record BlockedUserResponse(Long userId, String displayName, LocalDateTime blockedAt) {
}
