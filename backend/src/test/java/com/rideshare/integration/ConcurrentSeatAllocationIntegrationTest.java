package com.rideshare.integration;

import com.rideshare.common.exception.ApiException;
import com.rideshare.common.exception.ErrorCode;
import com.rideshare.ride.RideParticipantRepository;
import com.rideshare.ride.RideParticipationService;
import com.rideshare.ride.RideRepository;
import com.rideshare.ride.RideStatus;
import com.rideshare.ride.dto.JoinRideRequest;
import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * THE critical test: two (or more) students hit "join" for the last seat at the
 * same moment. Each call runs in its own thread and its own transaction against
 * real PostgreSQL. Exactly one may win; the database must never show more
 * occupied seats than exist.
 */
class ConcurrentSeatAllocationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RideParticipationService participationService;
    @Autowired
    private RideRepository rideRepository;
    @Autowired
    private RideParticipantRepository participantRepository;

    @RepeatedTest(5)
    void twoStudentsRacingForTheLastSeatExactlyOneWins() throws Exception {
        TestUser creator = registerStudent("creator");
        TestUser member1 = registerStudent("member1");
        TestUser member2 = registerStudent("member2");
        TestUser studentA = registerStudent("studenta");
        TestUser studentB = registerStudent("studentb");

        long rideId = createRide(creator, airportRide("09:30", 4));
        join(member1, rideId);
        join(member2, rideId);
        assertThat(rideRepository.findById(rideId).orElseThrow().getAvailableSeats()).isEqualTo(1);

        List<Outcome> outcomes = race(rideId, List.of(studentA.id(), studentB.id()));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(o -> !o.succeeded())
                .singleElement()
                .satisfies(o -> assertThat(o.errorCode()).isEqualTo(ErrorCode.RIDE_FULL));
        assertSeatInvariant(rideId, 4, 4);
        assertThat(rideRepository.findById(rideId).orElseThrow().getStatus()).isEqualTo(RideStatus.FULL);
    }

    @Test
    void tenStudentsRacingForThreeSeatsExactlyThreeWin() throws Exception {
        TestUser creator = registerStudent("creator");
        long rideId = createRide(creator, airportRide("09:30", 4));
        List<Long> contenders = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            contenders.add(registerStudent("racer" + i).id());
        }

        List<Outcome> outcomes = race(rideId, contenders);

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(3);
        assertThat(outcomes).filteredOn(o -> !o.succeeded())
                .hasSize(7)
                .allSatisfy(o -> assertThat(o.errorCode()).isEqualTo(ErrorCode.RIDE_FULL));
        assertSeatInvariant(rideId, 4, 4);
    }

    @Test
    void sameStudentDoubleClickingJoinGetsOnePlace() throws Exception {
        TestUser creator = registerStudent("creator");
        TestUser student = registerStudent("clicker");
        long rideId = createRide(creator, airportRide("09:30", 4));

        List<Outcome> outcomes = race(rideId, List.of(student.id(), student.id()));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(o -> !o.succeeded())
                .singleElement()
                .satisfies(o -> assertThat(o.errorCode()).isEqualTo(ErrorCode.ALREADY_JOINED));
        assertSeatInvariant(rideId, 4, 2);
    }

    /** Releases all joiners at the same instant and collects what happened to each. */
    private List<Outcome> race(long rideId, List<Long> userIds) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(userIds.size());
        CountDownLatch ready = new CountDownLatch(userIds.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Long userId : userIds) {
                Callable<Outcome> task = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        participationService.join(rideId, userId, new JoinRideRequest(1, null));
                        return new Outcome(true, null);
                    } catch (ApiException e) {
                        return new Outcome(false, e.getErrorCode());
                    }
                };
                futures.add(pool.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    /** occupied_seats must equal both the expected value and the sum of booked seats. */
    private void assertSeatInvariant(long rideId, int totalSeats, int expectedOccupied) {
        var ride = rideRepository.findById(rideId).orElseThrow();
        assertThat(ride.getTotalSeats()).isEqualTo(totalSeats);
        assertThat(ride.getOccupiedSeats()).isEqualTo(expectedOccupied);
        assertThat(ride.getAvailableSeats()).isGreaterThanOrEqualTo(0);
        Integer bookedInDb = jdbcTemplate.queryForObject(
                "select coalesce(sum(seats_booked), 0) from ride_participants where ride_id = ?", Integer.class, rideId);
        assertThat(bookedInDb).isEqualTo(expectedOccupied);
        assertThat(participantRepository.countByRideId(rideId)).isEqualTo(expectedOccupied);
    }

    private record Outcome(boolean succeeded, ErrorCode errorCode) {
    }
}
