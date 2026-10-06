package com.rideshare.ride;

import com.rideshare.common.exception.ApiException;
import com.rideshare.common.exception.ErrorCode;
import com.rideshare.notification.NotificationService;
import com.rideshare.notification.NotificationType;
import com.rideshare.notification.RealtimePublisher;
import com.rideshare.common.idempotency.IdempotencyService;
import com.rideshare.ride.dto.JoinRideRequest;
import com.rideshare.ride.waitlist.WaitlistEntryRepository;
import com.rideshare.ride.waitlist.WaitlistPromoter;
import com.rideshare.ride.waitlist.WaitlistProperties;
import com.rideshare.safety.BlockLookup;
import com.rideshare.user.User;
import com.rideshare.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.rideshare.support.TestRides.AIRPORT;
import static com.rideshare.support.TestRides.CAMPUS;
import static com.rideshare.support.TestRides.ride;
import static com.rideshare.support.TestRides.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Business rules of join/leave in isolation (locking itself is covered by the integration test). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RideParticipationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 3, 1, 12, 0);

    @Mock
    private RideLocker rideLocker;
    @Mock
    private RideParticipantRepository participantRepository;
    @Mock
    private OverlapGuard overlapGuard;
    @Mock
    private UserService userService;
    @Mock
    private BlockLookup blockLookup;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RealtimePublisher realtimePublisher;
    @Mock
    private RideService rideService;
    @Mock
    private WaitlistEntryRepository waitlistRepository;
    @Mock
    private WaitlistPromoter waitlistPromoter;
    @Mock
    private IdempotencyService idempotencyService;

    private RideParticipationService service;
    private Ride ride;
    private final User joiner = user(7L, "Joiner Person");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(ZonedDateTime.of(NOW, ZONE).toInstant(), ZONE);
        service = new RideParticipationService(rideLocker, participantRepository,
                new RidePolicy(RideValidatorTest.properties(), new WaitlistProperties(20)), overlapGuard, userService,
                blockLookup, notificationService, realtimePublisher, rideService, waitlistRepository, waitlistPromoter,
                idempotencyService, clock);
        ride = ride(1L, CAMPUS, AIRPORT, NOW.plusHours(4), 4, 3);
        when(userService.getActiveUser(7L)).thenReturn(joiner);
        when(rideLocker.lock(1L)).thenReturn(ride);
    }

    @Test
    void joiningTakesTheLastSeatMarksFullAndNotifies() {
        when(participantRepository.existsByRideIdAndUserId(1L, 7L)).thenReturn(false);
        when(participantRepository.findUserIdsByRideId(1L)).thenReturn(List.of(1001L), List.of(1001L, 7L));
        when(blockLookup.blockedRelations(7L)).thenReturn(Set.of());

        service.join(1L, 7L, new JoinRideRequest(1, null));

        assertThat(ride.getOccupiedSeats()).isEqualTo(4);
        assertThat(ride.getStatus()).isEqualTo(RideStatus.FULL);
        verify(participantRepository).save(any(RideParticipant.class));
        verify(notificationService).notifyUsers(eq(List.of(1001L)), eq(NotificationType.RIDE_JOINED), anyString(), eq(1L));
        verify(notificationService).notifyUsers(eq(List.of(1001L, 7L)), eq(NotificationType.RIDE_FULL), anyString(), eq(1L));
    }

    @Test
    void blockedStudentCannotJoinAndNothingIsSaved() {
        when(participantRepository.existsByRideIdAndUserId(1L, 7L)).thenReturn(false);
        when(participantRepository.findUserIdsByRideId(1L)).thenReturn(List.of(1001L));
        when(blockLookup.blockedRelations(7L)).thenReturn(Set.of(1001L));

        assertThatThrownBy(() -> service.join(1L, 7L, null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INTERACTION_BLOCKED);
        verify(participantRepository, never()).save(any());
        assertThat(ride.getOccupiedSeats()).isEqualTo(3);
    }

    @Test
    void requestingMoreSeatsThanLeftIsRideFull() {
        when(participantRepository.existsByRideIdAndUserId(1L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> service.join(1L, 7L, new JoinRideRequest(2, null)))
                .extracting("errorCode").isEqualTo(ErrorCode.RIDE_FULL);
        verify(participantRepository, never()).save(any());
    }

    @Test
    void rideCannotReplaceItself() {
        assertThatThrownBy(() -> service.join(1L, 7L, new JoinRideRequest(1, 1L)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void leavingFreesSeatsAndReopensAFullRide() {
        Ride full = ride(2L, CAMPUS, AIRPORT, NOW.plusHours(4), 3, 1);
        full.occupySeats(2);
        RideParticipant membership = new RideParticipant(full, joiner, 2, ParticipantRole.MEMBER, NOW);
        when(rideLocker.lock(2L)).thenReturn(full);
        when(participantRepository.findByRideIdAndUserId(2L, 7L)).thenReturn(Optional.of(membership));
        when(participantRepository.findUserIdsByRideId(2L)).thenReturn(List.of(1002L));

        service.leave(2L, 7L);

        assertThat(full.getOccupiedSeats()).isEqualTo(1);
        assertThat(full.getStatus()).isEqualTo(RideStatus.OPEN);
        verify(participantRepository).delete(membership);
        verify(notificationService).notifyUsers(eq(List.of(1002L)), eq(NotificationType.RIDE_LEFT), anyString(), eq(2L));
        // Freed seats are offered to the waitlist inside the same (locked) operation.
        verify(waitlistPromoter).promote(eq(full), any());
    }

    @Test
    void retriedJoinWithTheSameKeyDoesNotBookAgain() {
        when(idempotencyService.alreadyProcessed(7L, "key-12345", com.rideshare.common.idempotency.IdempotentOperation.JOIN_RIDE, 1L))
                .thenReturn(true);

        service.join(1L, 7L, new JoinRideRequest(1, null), "key-12345");

        verify(participantRepository, never()).save(any());
        assertThat(ride.getOccupiedSeats()).isEqualTo(3);
        verify(rideService).detailFor(ride, 7L);
    }
}
