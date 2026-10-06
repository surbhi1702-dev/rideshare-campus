package com.rideshare.ride;

import com.rideshare.common.exception.ConflictException;
import com.rideshare.common.exception.ErrorCode;
import com.rideshare.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Prevents a student from holding places in two active rides that leave at
 * (almost) the same time - otherwise one group would wait for a no-show.
 */
@Component
public class OverlapGuard {

    private static final List<Long> NO_RIDES = List.of(-1L);

    private final RideParticipantRepository participantRepository;
    private final int windowMinutes;

    public OverlapGuard(RideParticipantRepository participantRepository, AppProperties properties) {
        this.participantRepository = participantRepository;
        this.windowMinutes = properties.ride().overlapWindowMinutes();
    }

    public void requireNoOverlap(Long userId, LocalDateTime departureAt, Collection<Long> ignoredRideIds) {
        if (hasOverlap(userId, departureAt, ignoredRideIds)) {
            throw new ConflictException(ErrorCode.OVERLAPPING_RIDE,
                    "You already have an active ride within %d minutes of this time. Leave or cancel it first, "
                            .formatted(windowMinutes)
                            + "or join with 'replace my ride' to merge your trip.");
        }
    }

    /** Non-throwing variant, used when re-checking a waitlisted student at promotion time. */
    public boolean hasOverlap(Long userId, LocalDateTime departureAt, Collection<Long> ignoredRideIds) {
        return participantRepository.existsActiveRideInWindow(userId, RideStatus.ACTIVE,
                departureAt.minusMinutes(windowMinutes), departureAt.plusMinutes(windowMinutes),
                ignoredRideIds.isEmpty() ? NO_RIDES : ignoredRideIds);
    }
}
