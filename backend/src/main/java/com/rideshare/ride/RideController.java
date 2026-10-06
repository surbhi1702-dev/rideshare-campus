package com.rideshare.ride;

import com.rideshare.common.dto.PageResponse;
import com.rideshare.common.dto.Paging;
import com.rideshare.common.idempotency.IdempotencyService;
import com.rideshare.ride.dto.CreateRideRequest;
import com.rideshare.ride.dto.JoinRideRequest;
import com.rideshare.ride.dto.RideBrowseFilter;
import com.rideshare.ride.dto.RideDetailResponse;
import com.rideshare.ride.dto.RideSummaryResponse;
import com.rideshare.ride.dto.UpdateRideRequest;
import com.rideshare.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Rides as resources plus explicit state-transition actions (join/leave/cancel/
 * start/complete). Rides are never hard-deleted: cancelling keeps the history
 * members need, so there is intentionally no DELETE /api/rides/{id}.
 */
@RestController
@RequestMapping("/api/rides")
@Tag(name = "Rides")
public class RideController {

    private final RideService rideService;
    private final RideParticipationService participationService;

    public RideController(RideService rideService, RideParticipationService participationService) {
        this.rideService = rideService;
        this.participationService = participationService;
    }

    @PostMapping
    @Operation(summary = "Create a ride (you become its creator and first participant)",
            description = "201. 400 INVALID_DEPARTURE_TIME / INVALID_SEAT_COUNT / INVALID_ROUTE, 409 OVERLAPPING_RIDE.")
    public ResponseEntity<RideDetailResponse> create(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                     @Valid @RequestBody CreateRideRequest request) {
        RideDetailResponse created = rideService.create(currentUser.id(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.ride().id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "Browse open upcoming rides (text/date filters, paginated)",
            description = "For location-based ranked results use GET /api/rides/search.")
    public PageResponse<RideSummaryResponse> browse(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                    @RequestParam(required = false) String source,
                                                    @RequestParam(required = false) String destination,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(pattern = "HH:mm") LocalTime fromTime,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(pattern = "HH:mm") LocalTime toTime,
                                                    @RequestParam(required = false) Integer minSeats,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        RideBrowseFilter filter = new RideBrowseFilter(source, destination, date, fromTime, toTime, minSeats);
        return rideService.browse(filter, currentUser.id(),
                Paging.of(page, size, Sort.by("departureAt").ascending().and(Sort.by("id"))));
    }

    @GetMapping("/mine")
    @Operation(summary = "Rides I created or joined", description = "scope=upcoming (default) or past")
    public PageResponse<RideSummaryResponse> mine(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                  @RequestParam(defaultValue = "upcoming") String scope,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        boolean upcoming = !"past".equalsIgnoreCase(scope);
        Sort sort = upcoming ? Sort.by("departureAt").ascending() : Sort.by("departureAt").descending();
        return rideService.myRides(currentUser.id(), upcoming, Paging.of(page, size, sort));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ride details",
            description = "Members see names, phone numbers and the fare split; others see a privacy-reduced view.")
    public RideDetailResponse get(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id) {
        return rideService.get(id, currentUser.id());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update my ride (creator only, before departure)")
    public RideDetailResponse update(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id,
                                     @Valid @RequestBody UpdateRideRequest request) {
        return rideService.update(id, currentUser.id(), request);
    }

    @PostMapping("/{id}/join")
    @Operation(summary = "Join a ride",
            description = "Concurrency-safe; send an Idempotency-Key header to make retries safe. 409 RIDE_FULL, ALREADY_JOINED, RIDE_CANCELLED, RIDE_COMPLETED, "
                    + "RIDE_ALREADY_DEPARTED, OVERLAPPING_RIDE; 403 INTERACTION_BLOCKED.")
    public RideDetailResponse join(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id,
                                   @Valid @RequestBody(required = false) JoinRideRequest request,
                                   @RequestHeader(name = IdempotencyService.HEADER, required = false)
                                   String idempotencyKey) {
        return participationService.join(id, currentUser.id(), request, idempotencyKey);
    }

    @PostMapping("/{id}/leave")
    @Operation(summary = "Leave a ride you joined (frees your seats)",
            description = "409 NOT_A_PARTICIPANT, CREATOR_CANNOT_LEAVE (creators cancel instead), RIDE_ALREADY_DEPARTED.")
    public RideDetailResponse leave(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id) {
        return participationService.leave(id, currentUser.id());
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel my ride (creator only); all members are notified")
    public RideDetailResponse cancel(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id) {
        return rideService.cancel(id, currentUser.id());
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Mark the ride as started (creator only, near departure)")
    public RideDetailResponse start(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id) {
        return rideService.start(id, currentUser.id());
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Mark a started ride as completed (creator only)")
    public RideDetailResponse complete(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id) {
        return rideService.complete(id, currentUser.id());
    }
}
