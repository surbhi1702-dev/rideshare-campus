package com.rideshare.ride;

import com.rideshare.common.dto.PageResponse;
import com.rideshare.common.exception.ErrorCode;
import com.rideshare.common.exception.ResourceNotFoundException;
import com.rideshare.matching.MatchSuggestionNotifier;
import com.rideshare.notification.NotificationService;
import com.rideshare.notification.NotificationType;
import com.rideshare.notification.RealtimePublisher;
import com.rideshare.notification.dto.RideEvent;
import com.rideshare.ride.dto.CreateRideRequest;
import com.rideshare.ride.dto.RideBrowseFilter;
import com.rideshare.ride.dto.RideDetailResponse;
import com.rideshare.ride.dto.RideSummaryResponse;
import com.rideshare.ride.dto.UpdateRideRequest;
import com.rideshare.safety.BlockLookup;
import com.rideshare.user.User;
import com.rideshare.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ride lifecycle: create, read, update, cancel, start, complete.
 * Joining and leaving live in {@link RideParticipationService}.
 * Every state change locks the ride row first (see {@link RideLocker}).
 */
@Service
public class RideService {

    private static final Logger log = LoggerFactory.getLogger(RideService.class);

    private final RideRepository rideRepository;
    private final RideParticipantRepository participantRepository;
    private final RideLocker rideLocker;
    private final RideValidator rideValidator;
    private final RidePolicy ridePolicy;
    private final RideMapper rideMapper;
    private final OverlapGuard overlapGuard;
    private final UserService userService;
    private final BlockLookup blockLookup;
    private final NotificationService notificationService;
    private final RealtimePublisher realtimePublisher;
    private final MatchSuggestionNotifier matchSuggestionNotifier;
    private final Clock clock;

    public RideService(RideRepository rideRepository, RideParticipantRepository participantRepository,
                       RideLocker rideLocker, RideValidator rideValidator, RidePolicy ridePolicy, RideMapper rideMapper,
                       OverlapGuard overlapGuard, UserService userService, BlockLookup blockLookup,
                       NotificationService notificationService, RealtimePublisher realtimePublisher,
                       MatchSuggestionNotifier matchSuggestionNotifier, Clock clock) {
        this.rideRepository = rideRepository;
        this.participantRepository = participantRepository;
        this.rideLocker = rideLocker;
        this.rideValidator = rideValidator;
        this.ridePolicy = ridePolicy;
        this.rideMapper = rideMapper;
        this.overlapGuard = overlapGuard;
        this.userService = userService;
        this.blockLookup = blockLookup;
        this.notificationService = notificationService;
        this.realtimePublisher = realtimePublisher;
        this.matchSuggestionNotifier = matchSuggestionNotifier;
        this.clock = clock;
    }

    @Transactional
    public RideDetailResponse create(Long userId, CreateRideRequest request) {
        User creator = userService.getActiveUser(userId);
        RideDetails details = rideValidator.validateCreate(request);
        int creatorSeats = request.seatsForCreator() == null ? 1 : request.seatsForCreator();
        overlapGuard.requireNoOverlap(userId, details.departureAt(), List.of());

        Ride ride = rideRepository.save(new Ride(creator, details, creatorSeats));
        participantRepository.save(new RideParticipant(ride, creator, creatorSeats, ParticipantRole.CREATOR, now()));
        log.info("User {} created ride {} ({})", userId, ride.getId(), RideMessages.describe(ride));

        matchSuggestionNotifier.notifyOwnersOfSimilarRides(ride);
        return detailFor(ride, userId);
    }

    @Transactional(readOnly = true)
    public RideDetailResponse get(Long rideId, Long viewerId) {
        Ride ride = rideRepository.findWithCreatorById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RIDE_NOT_FOUND, "Ride not found"));
        return detailFor(ride, viewerId);
    }

    @Transactional(readOnly = true)
    public PageResponse<RideSummaryResponse> browse(RideBrowseFilter filter, Long viewerId, Pageable pageable) {
        Page<Ride> page = rideRepository.findAll(
                RideSpecifications.browse(filter, now(), blockLookup.blockedRelationsForQuery(viewerId)), pageable);
        return PageResponse.from(page, rideMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public PageResponse<RideSummaryResponse> myRides(Long userId, boolean upcoming, Pageable pageable) {
        Page<Ride> page = upcoming
                ? rideRepository.findUpcomingForUser(userId, now(), RideStatus.ACTIVE, RideStatus.STARTED, pageable)
                : rideRepository.findPastForUser(userId, now(), RideStatus.ACTIVE, RideStatus.STARTED, pageable);
        return PageResponse.from(page, rideMapper::toSummary);
    }

    @Transactional
    public RideDetailResponse update(Long rideId, Long userId, UpdateRideRequest request) {
        userService.getActiveUser(userId);
        Ride ride = rideLocker.lock(rideId);
        ridePolicy.checkEdit(ride, userId, now()).ifPresent(e -> {
            throw e;
        });
        RideDetails details = rideValidator.validateUpdate(request, ride);
        overlapGuard.requireNoOverlap(userId, details.departureAt(), List.of(rideId));

        ride.applyDetails(details);
        ride.refreshCapacityStatus();

        notifyOtherMembers(ride, userId, NotificationType.RIDE_UPDATED,
                "The creator updated the ride details: %s. Please check the new time and route."
                        .formatted(RideMessages.describe(ride)));
        realtimePublisher.publishRideEvent(ride, RideEvent.RideEventType.RIDE_UPDATED);
        return detailFor(ride, userId);
    }

    @Transactional
    public RideDetailResponse cancel(Long rideId, Long userId) {
        Ride ride = rideLocker.lock(rideId);
        ridePolicy.checkCancel(ride, userId).ifPresent(e -> {
            throw e;
        });
        ride.markCancelled();
        log.info("User {} cancelled ride {}", userId, rideId);

        notifyOtherMembers(ride, userId, NotificationType.RIDE_CANCELLED,
                "Ride %s was cancelled by its creator. Search again to find another group."
                        .formatted(RideMessages.describe(ride)));
        realtimePublisher.publishRideEvent(ride, RideEvent.RideEventType.RIDE_CANCELLED);
        return detailFor(ride, userId);
    }

    @Transactional
    public RideDetailResponse start(Long rideId, Long userId) {
        Ride ride = rideLocker.lock(rideId);
        ridePolicy.checkStart(ride, userId, now()).ifPresent(e -> {
            throw e;
        });
        ride.markStarted();
        notifyOtherMembers(ride, userId, NotificationType.RIDE_STARTED,
                "Ride %s has started.".formatted(RideMessages.describe(ride)));
        realtimePublisher.publishRideEvent(ride, RideEvent.RideEventType.RIDE_STARTED);
        return detailFor(ride, userId);
    }

    @Transactional
    public RideDetailResponse complete(Long rideId, Long userId) {
        Ride ride = rideLocker.lock(rideId);
        ridePolicy.checkComplete(ride, userId).ifPresent(e -> {
            throw e;
        });
        ride.markCompleted();
        notifyOtherMembers(ride, userId, NotificationType.RIDE_COMPLETED,
                "Ride %s is completed. Settle your share with the group.".formatted(RideMessages.describe(ride)));
        realtimePublisher.publishRideEvent(ride, RideEvent.RideEventType.RIDE_COMPLETED);
        return detailFor(ride, userId);
    }

    /** Builds the viewer-specific detail DTO (privacy rules applied in the mapper). */
    @Transactional(readOnly = true)
    public RideDetailResponse detailFor(Ride ride, Long viewerId) {
        List<RideParticipant> participants = participantRepository.findAllWithUserByRideId(ride.getId());
        RideParticipant viewer = participants.stream()
                .filter(p -> p.getUser().getId().equals(viewerId))
                .findFirst()
                .orElse(null);
        return rideMapper.toDetail(ride, participants, viewer,
                ridePolicy.actionsFor(ride, viewerId, viewer, now()));
    }

    private void notifyOtherMembers(Ride ride, Long actorId, NotificationType type, String message) {
        List<Long> recipients = participantRepository.findUserIdsByRideId(ride.getId()).stream()
                .filter(id -> !id.equals(actorId))
                .toList();
        notificationService.notifyUsers(recipients, type, message, ride.getId());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
