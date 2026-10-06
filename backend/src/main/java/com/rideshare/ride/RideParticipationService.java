package com.rideshare.ride;

import com.rideshare.common.exception.ConflictException;
import com.rideshare.common.idempotency.IdempotencyService;
import com.rideshare.common.idempotency.IdempotentOperation;
import com.rideshare.common.exception.ErrorCode;
import com.rideshare.common.exception.ForbiddenOperationException;
import com.rideshare.common.exception.InvalidRequestException;
import com.rideshare.notification.NotificationService;
import com.rideshare.notification.NotificationType;
import com.rideshare.notification.RealtimePublisher;
import com.rideshare.notification.dto.RideEvent;
import com.rideshare.ride.dto.JoinRideRequest;
import com.rideshare.ride.dto.RideDetailResponse;
import com.rideshare.ride.waitlist.WaitlistEntryRepository;
import com.rideshare.ride.waitlist.WaitlistPromoter;
import com.rideshare.safety.BlockLookup;
import com.rideshare.user.User;
import com.rideshare.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Seat allocation: joining and leaving rides.
 *
 * <h2>Concurrency</h2>
 * Both operations start by taking a PostgreSQL row lock on the ride
 * ({@code SELECT ... FOR UPDATE} via {@link RideLocker}). Two students racing
 * for the last seat are therefore serialised: the second transaction waits until
 * the first commits, then re-reads the ride, sees 0 free seats and gets
 * RIDE_FULL. The unique (ride_id, user_id) constraint and the
 * {@code occupied_seats <= total_seats} CHECK constraint back this up in the
 * database itself.
 *
 * <h2>Waitlist and retries</h2>
 * Leaving hands the freed seats to the waitlist ({@link WaitlistPromoter}) while
 * the row is still locked, so nobody can grab them in between. Joining accepts an
 * Idempotency-Key ({@link IdempotencyService}) so a retried request never books twice.
 */
@Service
public class RideParticipationService {

    private static final Logger log = LoggerFactory.getLogger(RideParticipationService.class);

    private final RideLocker rideLocker;
    private final RideParticipantRepository participantRepository;
    private final RidePolicy ridePolicy;
    private final OverlapGuard overlapGuard;
    private final UserService userService;
    private final BlockLookup blockLookup;
    private final NotificationService notificationService;
    private final RealtimePublisher realtimePublisher;
    private final RideService rideService;
    private final WaitlistEntryRepository waitlistRepository;
    private final WaitlistPromoter waitlistPromoter;
    private final IdempotencyService idempotencyService;
    private final Clock clock;

    public RideParticipationService(RideLocker rideLocker, RideParticipantRepository participantRepository,
                                    RidePolicy ridePolicy, OverlapGuard overlapGuard, UserService userService,
                                    BlockLookup blockLookup, NotificationService notificationService,
                                    RealtimePublisher realtimePublisher, RideService rideService,
                                    WaitlistEntryRepository waitlistRepository, WaitlistPromoter waitlistPromoter,
                                    IdempotencyService idempotencyService, Clock clock) {
        this.rideLocker = rideLocker;
        this.participantRepository = participantRepository;
        this.ridePolicy = ridePolicy;
        this.overlapGuard = overlapGuard;
        this.userService = userService;
        this.blockLookup = blockLookup;
        this.notificationService = notificationService;
        this.realtimePublisher = realtimePublisher;
        this.rideService = rideService;
        this.waitlistRepository = waitlistRepository;
        this.waitlistPromoter = waitlistPromoter;
        this.idempotencyService = idempotencyService;
        this.clock = clock;
    }

    @Transactional
    public RideDetailResponse join(Long rideId, Long userId, JoinRideRequest request) {
        return join(rideId, userId, request, null);
    }

    /**
     * @param idempotencyKey optional; a repeated key for the same ride returns the current
     *                       state instead of joining again
     */
    @Transactional
    public RideDetailResponse join(Long rideId, Long userId, JoinRideRequest request, String idempotencyKey) {
        User user = userService.getActiveUser(userId);
        int seats = request == null ? 1 : request.seatsOrDefault();
        Long replaceRideId = request == null ? null : request.replaceRideId();
        if (rideId.equals(replaceRideId)) {
            throw new InvalidRequestException(ErrorCode.INVALID_REQUEST, "A ride cannot replace itself");
        }

        LockedRides locked = lockInIdOrder(rideId, replaceRideId);
        Ride ride = locked.target();
        LocalDateTime now = LocalDateTime.now(clock);
        if (idempotencyService.alreadyProcessed(userId, idempotencyKey, IdempotentOperation.JOIN_RIDE, rideId)) {
            return rideService.detailFor(ride, userId);
        }

        boolean alreadyMember = participantRepository.existsByRideIdAndUserId(rideId, userId);
        ridePolicy.checkJoin(ride, alreadyMember, seats, now).ifPresent(e -> {
            throw e;
        });

        List<Long> existingMemberIds = participantRepository.findUserIdsByRideId(rideId);
        Set<Long> blocked = blockLookup.blockedRelations(userId);
        if (existingMemberIds.stream().anyMatch(blocked::contains)) {
            // Deliberately vague: never reveal who blocked whom.
            throw new ForbiddenOperationException(ErrorCode.INTERACTION_BLOCKED, "You can't join this ride");
        }

        if (locked.replaced() != null) {
            cancelReplacedRide(locked.replaced(), userId, now);
        }
        overlapGuard.requireNoOverlap(userId, ride.getDepartureAt(),
                replaceRideId == null ? List.of() : List.of(replaceRideId));

        participantRepository.save(new RideParticipant(ride, user, seats, ParticipantRole.MEMBER, now));
        ride.occupySeats(seats);
        // Joining directly (e.g. a seat freed that their request did not fit before) ends their queue spot.
        waitlistRepository.deleteByRideIdAndUserId(rideId, userId);
        idempotencyService.record(userId, idempotencyKey, IdempotentOperation.JOIN_RIDE, rideId);
        participantRepository.flush();
        log.info("User {} joined ride {} with {} seat(s) -> {}", userId, rideId, seats, RideMessages.seats(ride));

        notificationService.notifyUsers(existingMemberIds, NotificationType.RIDE_JOINED,
                "%s joined %s - %s.".formatted(user.getDisplayName(), RideMessages.describe(ride),
                        RideMessages.seats(ride)), rideId);
        if (ride.getStatus() == RideStatus.FULL) {
            List<Long> everyone = participantRepository.findUserIdsByRideId(rideId);
            notificationService.notifyUsers(everyone, NotificationType.RIDE_FULL,
                    "Ride %s is now full. Coordinate the pickup with your group.".formatted(RideMessages.describe(ride)),
                    rideId);
        }
        realtimePublisher.publishRideEvent(ride, RideEvent.RideEventType.PARTICIPANT_JOINED);
        return rideService.detailFor(ride, userId);
    }

    @Transactional
    public RideDetailResponse leave(Long rideId, Long userId) {
        Ride ride = rideLocker.lock(rideId);
        RideParticipant participant = participantRepository.findByRideIdAndUserId(rideId, userId).orElse(null);
        ridePolicy.checkLeave(ride, participant, LocalDateTime.now(clock)).ifPresent(e -> {
            throw e;
        });

        User leaver = participant.getUser();
        participantRepository.delete(participant);
        ride.releaseSeats(participant.getSeatsBooked());
        participantRepository.flush();
        log.info("User {} left ride {} -> {}", userId, rideId, RideMessages.seats(ride));

        List<Long> remaining = participantRepository.findUserIdsByRideId(rideId);
        // Still holding the row lock: the freed seats go to the queue before anyone else can take them.
        List<Long> promoted = waitlistPromoter.promote(ride, LocalDateTime.now(clock));
        String seatsNow = promoted.isEmpty()
                ? "%d seat(s) free again".formatted(ride.getAvailableSeats())
                : "the seat went to the next student on the waitlist";
        notificationService.notifyUsers(remaining, NotificationType.RIDE_LEFT,
                "%s left %s - %s.".formatted(leaver.getDisplayName(), RideMessages.describe(ride), seatsNow), rideId);
        realtimePublisher.publishRideEvent(ride, RideEvent.RideEventType.PARTICIPANT_LEFT);
        return rideService.detailFor(ride, userId);
    }

    /**
     * "Merge": the student's own ride is cancelled in the same transaction as the
     * join, so they never end up with zero rides or two rides.
     */
    private void cancelReplacedRide(Ride replaced, Long userId, LocalDateTime now) {
        if (!replaced.isCreatedBy(userId)) {
            throw new ForbiddenOperationException("You can only replace a ride you created");
        }
        if (!RideStatus.EDITABLE.contains(replaced.getStatus())) {
            throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION, "The ride to replace is no longer active");
        }
        if (participantRepository.countByRideId(replaced.getId()) > 1) {
            throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Your ride already has other members - cancel it explicitly so they are informed");
        }
        replaced.markCancelled();
        realtimePublisher.publishRideEvent(replaced, RideEvent.RideEventType.RIDE_CANCELLED);
        log.info("User {} merged: cancelled own ride {}", userId, replaced.getId());
    }

    /** Always lock the lower id first so two opposite merges cannot deadlock. */
    private LockedRides lockInIdOrder(Long targetId, Long replacedId) {
        if (replacedId == null) {
            return new LockedRides(rideLocker.lock(targetId), null);
        }
        if (targetId < replacedId) {
            Ride target = rideLocker.lock(targetId);
            return new LockedRides(target, rideLocker.lock(replacedId));
        }
        Ride replaced = rideLocker.lock(replacedId);
        return new LockedRides(rideLocker.lock(targetId), replaced);
    }

    private record LockedRides(Ride target, Ride replaced) {
    }
}
