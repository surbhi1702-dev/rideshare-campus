package com.rideshare.notification.dto;

import com.rideshare.notification.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String message,
        Long rideId,
        boolean read,
        LocalDateTime createdAt
) {
}
