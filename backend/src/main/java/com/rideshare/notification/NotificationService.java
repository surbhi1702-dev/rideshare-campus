package com.rideshare.notification;

import com.rideshare.common.dto.PageResponse;
import com.rideshare.common.exception.ErrorCode;
import com.rideshare.common.exception.ResourceNotFoundException;
import com.rideshare.notification.dto.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final RealtimePublisher realtimePublisher;

    public NotificationService(NotificationRepository notificationRepository, NotificationMapper notificationMapper,
                               RealtimePublisher realtimePublisher) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
        this.realtimePublisher = realtimePublisher;
    }

    /**
     * Stores one notification per recipient (duplicates removed) inside the
     * caller's transaction and schedules a real-time push after commit.
     */
    @Transactional
    public void notifyUsers(Collection<Long> recipientIds, NotificationType type, String message, Long rideId) {
        List<Notification> notifications = new LinkedHashSet<>(recipientIds).stream()
                .map(recipientId -> new Notification(recipientId, type, message, rideId))
                .toList();
        if (notifications.isEmpty()) {
            return;
        }
        notificationRepository.saveAll(notifications).forEach(saved ->
                realtimePublisher.publishNotification(saved.getRecipientId(), notificationMapper.toResponse(saved)));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByRecipientIdAndReadFalse(userId, pageable)
                : notificationRepository.findByRecipientId(userId, pageable);
        return PageResponse.from(page, notificationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        // Looking up by (id, recipient) means another user's notification is simply "not found".
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOTIFICATION_NOT_FOUND, "Notification not found"));
        notification.markRead();
        return notificationMapper.toResponse(notification);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId);
    }
}
