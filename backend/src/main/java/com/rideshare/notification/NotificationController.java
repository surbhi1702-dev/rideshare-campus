package com.rideshare.notification;

import com.rideshare.common.dto.PageResponse;
import com.rideshare.common.dto.Paging;
import com.rideshare.notification.dto.NotificationResponse;
import com.rideshare.notification.dto.UnreadCountResponse;
import com.rideshare.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "In-app notifications (also pushed live on /user/queue/notifications)")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List my notifications, newest first")
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                   @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return notificationService.list(currentUser.id(), unreadOnly,
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Number of unread notifications")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return new UnreadCountResponse(notificationService.unreadCount(currentUser.id()));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read", description = "404 if it does not exist or is not yours.")
    public NotificationResponse markRead(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id) {
        return notificationService.markRead(currentUser.id(), id);
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all my notifications as read")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        notificationService.markAllRead(currentUser.id());
        return ResponseEntity.noContent().build();
    }
}
