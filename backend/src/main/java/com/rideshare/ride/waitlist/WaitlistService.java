package com.rideshare.ride.waitlist;

import com.rideshare.common.exception.ConflictException;
import com.rideshare.common.exception.ErrorCode;
import com.rideshare.common.exception.ForbiddenOperationException;
import com.rideshare.common.idempotency.IdempotencyService;
import com.rideshare.common.idempotency.IdempotentOperation;
import com.rideshare.ride.OverlapGuard;
import com.rideshare.ride.Ride;
import com.rideshare.ride.RideLocker;
import com.rideshare.ride.RideParticipantRepository;
import com.rideshare.ride.RidePolicy;
import com.rideshare.ride.RideService;
import com.rideshare.ride.dto.RideDetailResponse;
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
 * Joining and leaving a ride's waitlist. Both lock the ride row first, like every
 * other seat-related operation, so queue changes and promotions never interleave.
 */
@Service
public class WaitlistService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);

    private final RideLocker rideLocker;
    private final WaitlistEntryRepository waitlistRepository;
    private final RideParticipantRepository participantRepository;
    private final RidePolicy ridePolicy;
    private final OverlapGuard overlapGuard;
    private final BlockLookup blockLookup;
    private final UserService userService;
    private final RideService rideService;
    private final IdempotencyService idempotencyService;
    private final Clock clock;

    public WaitlistService(RideLocker rideLocker, WaitlistEntryRepository waitlistRepository,
                           RideParticipantRepository participantRepository, RidePolicy ridePolicy,
                           OverlapGuard overlapGuard, BlockLookup blockLookup, UserService userService,
                           RideService rideService, IdempotencyService idempotencyService, Clock clock) {
        this.rideLocker = rideLocker;
        this.waitlistRepository = waitlistRepository;
        this.participantRepository = participantRepository;
        this.ridePolicy = ridePolicy;
        this.overlapGuard = overlapGuard;
        this.blockLookup = blockLookup;
        this.userService = userService;
        this.rideService = rideService;
        this.idempotencyService = idempotencyService;
        this.clock = clock;
    }

    @Transactional
    public RideDetailResponse join(Long rideId, Long userId, int seats, String idempotencyKey) {
        User user = userService.getActiveUser(userId);
        Ride ride = rideLocker.lock(rideId);
        if (idempotencyService.alreadyProcessed(userId, idempotencyKey, IdempotentOperation.JOIN_WAITLIST, rideId)) {
            return rideService.detailFor(ride, userId);
        }
        LocalDateTime now = LocalDateTime.now(clock);

        ridePolicy.checkJoinWaitlist(ride,
                participantRepository.existsByRideIdAndUserId(rideId, userId),
                waitlistRepository.existsByRideIdAndUserId(rideId, userId),
                seats, waitlistRepository.countByRideId(rideId), now).ifPresent(e -> {
            throw e;
        });
        Set<Long> blocked = blockLookup.blockedRelations(userId);
        if (participantRepository.findUserIdsByRideId(rideId).stream().anyMatch(blocked::contains)) {
            throw new ForbiddenOperationException(ErrorCode.INTERACTION_BLOCKED, "You can't join this ride");
        }
        overlapGuard.requireNoOverlap(userId, ride.getDepartureAt(), List.of());

        waitlistRepository.save(new WaitlistEntry(ride, user, seats, now));
        idempotencyService.record(userId, idempotencyKey, IdempotentOperation.JOIN_WAITLIST, rideId);
        waitlistRepository.flush();
        log.info("User {} queued for ride {} ({} seat(s))", userId, rideId, seats);
        return rideService.detailFor(ride, userId);
    }

    @Transactional
    public RideDetailResponse leave(Long rideId, Long userId) {
        Ride ride = rideLocker.lock(rideId);
        if (waitlistRepository.deleteByRideIdAndUserId(rideId, userId) == 0) {
            throw new ConflictException(ErrorCode.NOT_WAITLISTED, "You are not on this ride's waitlist");
        }
        log.info("User {} left the waitlist of ride {}", userId, rideId);
        return rideService.detailFor(ride, userId);
    }
}
