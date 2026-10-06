package com.rideshare.ride;

import com.rideshare.common.exception.ApiException;
import com.rideshare.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.rideshare.support.TestRides.AIRPORT;
import static com.rideshare.support.TestRides.CAMPUS;
import static com.rideshare.support.TestRides.ride;
import static org.assertj.core.api.Assertions.assertThat;

class RidePolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 3, 1, 12, 0);

    private final RidePolicy policy = new RidePolicy(RideValidatorTest.properties());

    private static ErrorCode code(Optional<ApiException> violation) {
        return violation.map(ApiException::getErrorCode).orElse(null);
    }

    @Test
    void openFutureRideWithSeatsCanBeJoined() {
        Ride ride = ride(1, CAMPUS, AIRPORT, NOW.plusHours(3), 4, 1);
        assertThat(policy.checkJoin(ride, false, 1, NOW)).isEmpty();
    }

    @Test
    void fullRideCannotBeJoined() {
        Ride ride = ride(1, CAMPUS, AIRPORT, NOW.plusHours(3), 4, 1);
        ride.occupySeats(3);
        assertThat(ride.getStatus()).isEqualTo(RideStatus.FULL);
        assertThat(code(policy.checkJoin(ride, false, 1, NOW))).isEqualTo(ErrorCode.RIDE_FULL);
    }

    @Test
    void requestingMoreSeatsThanAvailableIsRideFull() {
        Ride ride = ride(1, CAMPUS, AIRPORT, NOW.plusHours(3), 4, 3);
        assertThat(code(policy.checkJoin(ride, false, 2, NOW))).isEqualTo(ErrorCode.RIDE_FULL);
    }

    @Test
    void duplicateJoinIsRejected() {
        Ride ride = ride(1, CAMPUS, AIRPORT, NOW.plusHours(3), 4, 1);
        assertThat(code(policy.checkJoin(ride, true, 1, NOW))).isEqualTo(ErrorCode.ALREADY_JOINED);
    }

    @Test
    void cancelledCompletedStartedAndDepartedRidesCannotBeJoined() {
        Ride cancelled = ride(1, CAMPUS, AIRPORT, NOW.plusHours(3), 4, 1);
        cancelled.markCancelled();
        Ride completed = ride(2, CAMPUS, AIRPORT, NOW.plusHours(3), 4, 1);
        completed.markCompleted();
        Ride started = ride(3, CAMPUS, AIRPORT, NOW.plusHours(3), 4, 1);
        started.markStarted();
        Ride departed = ride(4, CAMPUS, AIRPORT, NOW.minusMinutes(1), 4, 1);

        assertThat(code(policy.checkJoin(cancelled, false, 1, NOW))).isEqualTo(ErrorCode.RIDE_CANCELLED);
        assertThat(code(policy.checkJoin(completed, false, 1, NOW))).isEqualTo(ErrorCode.RIDE_COMPLETED);
        assertThat(code(policy.checkJoin(started, false, 1, NOW))).isEqualTo(ErrorCode.RIDE_ALREADY_STARTED);
        assertThat(code(policy.checkJoin(departed, false, 1, NOW))).isEqualTo(ErrorCode.RIDE_ALREADY_DEPARTED);
    }

    @Test
    void onlyCreatorMayEditOrCancel() {
        Ride ride = ride(1, CAMPUS, AIRPORT, NOW.plusHours(3), 4, 1);
        Long creatorId = ride.getCreator().getId();

        assertThat(policy.checkEdit(ride, creatorId, NOW)).isEmpty();
        assertThat(policy.checkCancel(ride, creatorId)).isEmpty();
        assertThat(code(policy.checkEdit(ride, 999L, NOW))).isEqualTo(ErrorCode.ACCESS_DENIED);
        assertThat(code(policy.checkCancel(ride, 999L))).isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void startIsOnlyAllowedCloseToDeparture() {
        Ride ride = ride(1, CAMPUS, AIRPORT, NOW.plusHours(3), 4, 1);
        Long creatorId = ride.getCreator().getId();

        assertThat(code(policy.checkStart(ride, creatorId, NOW))).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
        assertThat(policy.checkStart(ride, creatorId, NOW.plusHours(2))).isEmpty();
    }

    @Test
    void onlyStartedRideCanBeCompleted() {
        Ride ride = ride(1, CAMPUS, AIRPORT, NOW.plusHours(3), 4, 1);
        Long creatorId = ride.getCreator().getId();

        assertThat(code(policy.checkComplete(ride, creatorId))).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
        ride.markStarted();
        assertThat(policy.checkComplete(ride, creatorId)).isEmpty();
    }

    @Test
    void seatAccountingTogglesOpenAndFull() {
        Ride ride = ride(1, CAMPUS, AIRPORT, NOW.plusHours(3), 3, 1);
        ride.occupySeats(2);
        assertThat(ride.getStatus()).isEqualTo(RideStatus.FULL);
        ride.releaseSeats(1);
        assertThat(ride.getStatus()).isEqualTo(RideStatus.OPEN);
        assertThat(ride.getAvailableSeats()).isEqualTo(1);
    }
}
