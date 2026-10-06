package com.rideshare.ride.waitlist;

import com.rideshare.common.idempotency.IdempotencyService;
import com.rideshare.ride.dto.RideDetailResponse;
import com.rideshare.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rides/{id}/waitlist")
@Tag(name = "Waitlist")
public class WaitlistController {

    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @PostMapping
    @Operation(summary = "Queue for a full ride; you are added automatically when seats free up",
            description = "Send an Idempotency-Key header to make retries safe. 409 SEATS_AVAILABLE (join directly), "
                    + "ALREADY_WAITLISTED, ALREADY_JOINED, WAITLIST_FULL, OVERLAPPING_RIDE; 403 INTERACTION_BLOCKED.")
    public RideDetailResponse join(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id,
                                   @Valid @RequestBody(required = false) JoinWaitlistRequest request,
                                   @RequestHeader(name = IdempotencyService.HEADER, required = false)
                                   String idempotencyKey) {
        int seats = request == null ? 1 : request.seatsOrDefault();
        return waitlistService.join(id, currentUser.id(), seats, idempotencyKey);
    }

    @DeleteMapping
    @Operation(summary = "Leave the waitlist", description = "409 NOT_WAITLISTED.")
    public RideDetailResponse leave(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id) {
        return waitlistService.leave(id, currentUser.id());
    }
}
