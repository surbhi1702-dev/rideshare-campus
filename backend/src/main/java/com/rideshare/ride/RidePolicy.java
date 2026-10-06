package com.rideshare.ride;

import com.rideshare.common.exception.AlreadyJoinedException;
import com.rideshare.common.exception.ApiException;
import com.rideshare.common.exception.ConflictException;
import com.rideshare.common.exception.ErrorCode;
import com.rideshare.common.exception.ForbiddenOperationException;
import com.rideshare.common.exception.RideFullException;
import com.rideshare.config.AppProperties;
import com.rideshare.ride.dto.RideActions;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Single source of truth for "may user X do action Y on this ride right now?".
 * Each check returns the exception that explains WHY NOT (or empty if allowed);
 * services throw it, and the detail endpoint turns the same checks into booleans
 * so the UI only offers valid actions.
 */
@Component
public class RidePolicy {

    private final AppProperties.Ride rules;

    public RidePolicy(AppProperties properties) {
        this.rules = properties.ride();
    }

    public Optional<ApiException> checkJoin(Ride ride, boolean alreadyMember, int seats, LocalDateTime now) {
        if (alreadyMember) {
            return Optional.of(new AlreadyJoinedException());
        }
        Optional<ApiException> state = checkStillEditable(ride, now);
        if (state.isPresent()) {
            return state;
        }
        if (ride.getAvailableSeats() < seats) {
            return Optional.of(new RideFullException(seats, ride.getAvailableSeats()));
        }
        return Optional.empty();
    }

    public Optional<ApiException> checkLeave(Ride ride, RideParticipant participant, LocalDateTime now) {
        if (participant == null) {
            return Optional.of(new ConflictException(ErrorCode.NOT_A_PARTICIPANT, "You are not part of this ride"));
        }
        if (participant.getRole() == ParticipantRole.CREATOR) {
            return Optional.of(new ConflictException(ErrorCode.CREATOR_CANNOT_LEAVE,
                    "You created this ride - cancel it instead of leaving"));
        }
        return checkStillEditable(ride, now);
    }

    public Optional<ApiException> checkEdit(Ride ride, Long userId, LocalDateTime now) {
        if (!ride.isCreatedBy(userId)) {
            return Optional.of(notCreator("edit"));
        }
        return checkStillEditable(ride, now);
    }

    /** Cancelling is allowed until the ride has started, even if departure time passed (a no-show trip). */
    public Optional<ApiException> checkCancel(Ride ride, Long userId) {
        if (!ride.isCreatedBy(userId)) {
            return Optional.of(notCreator("cancel"));
        }
        return checkStatusEditable(ride);
    }

    public Optional<ApiException> checkStart(Ride ride, Long userId, LocalDateTime now) {
        if (!ride.isCreatedBy(userId)) {
            return Optional.of(notCreator("start"));
        }
        Optional<ApiException> state = checkStatusEditable(ride);
        if (state.isPresent()) {
            return state;
        }
        if (now.isBefore(ride.getDepartureAt().minusMinutes(rules.startEarlyWindowMinutes()))) {
            return Optional.of(new ConflictException(ErrorCode.INVALID_STATE_TRANSITION,
                    "A ride can be started at most %d minutes before departure".formatted(rules.startEarlyWindowMinutes())));
        }
        return Optional.empty();
    }

    public Optional<ApiException> checkComplete(Ride ride, Long userId) {
        if (!ride.isCreatedBy(userId)) {
            return Optional.of(notCreator("complete"));
        }
        if (ride.getStatus() != RideStatus.STARTED) {
            return Optional.of(new ConflictException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only a started ride can be marked completed"));
        }
        return Optional.empty();
    }

    public RideActions actionsFor(Ride ride, Long viewerId, RideParticipant viewerParticipation, LocalDateTime now) {
        boolean member = viewerParticipation != null;
        return new RideActions(
                checkJoin(ride, member, 1, now).isEmpty(),
                member && checkLeave(ride, viewerParticipation, now).isEmpty(),
                checkEdit(ride, viewerId, now).isEmpty(),
                checkCancel(ride, viewerId).isEmpty(),
                checkStart(ride, viewerId, now).isEmpty(),
                checkComplete(ride, viewerId).isEmpty());
    }

    private Optional<ApiException> checkStillEditable(Ride ride, LocalDateTime now) {
        Optional<ApiException> state = checkStatusEditable(ride);
        if (state.isPresent()) {
            return state;
        }
        if (!ride.getDepartureAt().isAfter(now)) {
            return Optional.of(new ConflictException(ErrorCode.RIDE_ALREADY_DEPARTED,
                    "This ride's departure time has passed"));
        }
        return Optional.empty();
    }

    private static Optional<ApiException> checkStatusEditable(Ride ride) {
        return switch (ride.getStatus()) {
            case OPEN, FULL -> Optional.empty();
            case CANCELLED -> Optional.of(new ConflictException(ErrorCode.RIDE_CANCELLED, "This ride was cancelled"));
            case COMPLETED -> Optional.of(new ConflictException(ErrorCode.RIDE_COMPLETED, "This ride is already completed"));
            case STARTED -> Optional.of(new ConflictException(ErrorCode.RIDE_ALREADY_STARTED, "This ride has already started"));
        };
    }

    private static ApiException notCreator(String action) {
        return new ForbiddenOperationException("Only the ride creator can " + action + " this ride");
    }
}
