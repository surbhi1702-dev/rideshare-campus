package com.rideshare.auth.dto;

import com.rideshare.user.dto.UserProfileResponse;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserProfileResponse user
) {
}
