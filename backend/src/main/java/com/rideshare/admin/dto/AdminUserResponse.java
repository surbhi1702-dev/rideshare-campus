package com.rideshare.admin.dto;

import com.rideshare.user.Role;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String name,
        String email,
        String phoneNumber,
        Role role,
        boolean active,
        LocalDateTime createdAt
) {
}
