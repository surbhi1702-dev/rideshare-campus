package com.rideshare.notification;

import com.rideshare.notification.dto.NotificationResponse;
import com.rideshare.notification.dto.RideEvent;
import com.rideshare.ride.Ride;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

/**
 * Pushes WebSocket (STOMP) messages. Messages are sent only AFTER the database
 * transaction commits, so a client can never be told "someone joined" for a join
 * that was later rolled back. Delivery is best-effort: the persisted
 * notification and the REST API remain the source of truth.
 *
 * <ul>
 *   <li>{@code /topic/rides/{id}} - seat/status changes, for anyone viewing that ride</li>
 *   <li>{@code /user/queue/notifications} - a user's own new notifications</li>
 * </ul>
 */
@Component
public class RealtimePublisher {

    public static final String RIDE_TOPIC_PREFIX = "/topic/rides/";
    public static final String USER_NOTIFICATION_QUEUE = "/queue/notifications";

    private static final Logger log = LoggerFactory.getLogger(RealtimePublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishRideEvent(Ride ride, RideEvent.RideEventType type) {
        RideEvent event = new RideEvent(ride.getId(), type, ride.getStatus(), ride.getOccupiedSeats(),
                ride.getTotalSeats(), ride.getAvailableSeats(), Instant.now());
        afterCommit(() -> messagingTemplate.convertAndSend(RIDE_TOPIC_PREFIX + event.rideId(), event));
    }

    public void publishNotification(Long userId, NotificationResponse notification) {
        afterCommit(() -> messagingTemplate.convertAndSendToUser(String.valueOf(userId), USER_NOTIFICATION_QUEUE,
                notification));
    }

    private void afterCommit(Runnable action) {
        Runnable safeAction = () -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                log.warn("Real-time push failed (clients will catch up via REST): {}", e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeAction.run();
                }
            });
        } else {
            safeAction.run();
        }
    }
}
