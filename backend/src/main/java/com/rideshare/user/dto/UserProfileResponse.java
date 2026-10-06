package com.rideshare.user.dto;

import com.rideshare.user.Role;

import java.time.LocalDateTime;

/** The signed-in user's own profile (the only place their full details are returned). */
public record UserProfileResponse(
        Long id,
        String name,
        String email,
        String phoneNumber,
        Role role,
        LocalDateTime createdAt
) {
}
