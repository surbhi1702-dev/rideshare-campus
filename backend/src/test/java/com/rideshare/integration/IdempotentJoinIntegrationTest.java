package com.rideshare.integration;

import com.rideshare.common.exception.ApiException;
import com.rideshare.ride.RideParticipationService;
import com.rideshare.ride.RideRepository;
import com.rideshare.ride.dto.JoinRideRequest;
import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotentJoinIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RideParticipationService participationService;
    @Autowired
    private RideRepository rideRepository;

    @Test
    void retryWithTheSameKeyReturnsOkWithoutBookingAgain() throws Exception {
        TestUser creator = registerStudent("creator");
        TestUser joiner = registerStudent("joiner");
        long rideId = createRide(creator, airportRide("09:30", 4));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(authed(post("/api/rides/{id}/join", rideId), joiner)
                            .header("Idempotency-Key", "join-attempt-0001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.viewerRole").value("MEMBER"))
                    .andExpect(jsonPath("$.ride.occupiedSeats").value(2));
        }
        // Without a key, a second join is an ordinary duplicate.
        mockMvc.perform(authed(post("/api/rides/{id}/join", rideId), joiner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
    }

    @Test
    void reusingAKeyForAnotherRideIsRejected() throws Exception {
        TestUser creator = registerStudent("creator");
        TestUser joiner = registerStudent("joiner");
        long first = createRide(creator, airportRide("09:30", 4));
        long second = createRide(registerStudent("other"), airportRide("15:30", 4));

        mockMvc.perform(authed(post("/api/rides/{id}/join", first), joiner).header("Idempotency-Key", "same-key-123"))
                .andExpect(status().isOk());
        mockMvc.perform(authed(post("/api/rides/{id}/join", second), joiner).header("Idempotency-Key", "same-key-123"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void malformedKeyIsRejected() throws Exception {
        TestUser creator = registerStudent("creator");
        TestUser joiner = registerStudent("joiner");
        long rideId = createRide(creator, airportRide("09:30", 4));

        mockMvc.perform(authed(post("/api/rides/{id}/join", rideId), joiner).header("Idempotency-Key", "bad key!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
    }

    /** A double-tap sends the same request twice at once: one seat is booked, both calls succeed. */
    @Test
    void twoConcurrentCopiesOfTheSameRequestBookOnce() throws Exception {
        TestUser creator = registerStudent("creator");
        TestUser joiner = registerStudent("joiner");
        long rideId = createRide(creator, airportRide("09:30", 4));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    participationService.join(rideId, joiner.id(), new JoinRideRequest(1, null), "double-tap-key");
                    return true;
                } catch (ApiException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        List<Boolean> results = new ArrayList<>();
        for (Future<Boolean> f : futures) {
            results.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(results).containsOnly(true);
        assertThat(rideRepository.findById(rideId).orElseThrow().getOccupiedSeats()).isEqualTo(2);
    }
}
