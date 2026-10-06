package com.rideshare.notification;

import com.rideshare.notification.dto.NotificationResponse;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getMessage(),
                notification.getRideId(), notification.isRead(), notification.getCreatedAt());
    }
}
