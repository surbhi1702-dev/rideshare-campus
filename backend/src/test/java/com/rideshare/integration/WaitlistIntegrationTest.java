package com.rideshare.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.rideshare.ride.RideParticipationService;
import com.rideshare.ride.RideRepository;
import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WaitlistIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RideParticipationService participationService;
    @Autowired
    private RideRepository rideRepository;

    @Test
    void waiterSeesTheirPositionAndIsPromotedWhenAMemberLeaves() throws Exception {
        TestUser creator = registerStudent("creator");
        TestUser member = registerStudent("member");
        TestUser first = registerStudent("first");
        TestUser second = registerStudent("second");
        long rideId = createRide(creator, airportRide("09:30", 2));
        join(member, rideId);

        queue(first, rideId).andExpect(status().isOk())
                .andExpect(jsonPath("$.waitlist.size").value(1))
                .andExpect(jsonPath("$.waitlist.viewerPosition").value(1))
                .andExpect(jsonPath("$.actions.canLeaveWaitlist").value(true));
        queue(second, rideId).andExpect(jsonPath("$.waitlist.viewerPosition").value(2));

        mockMvc.perform(authed(post("/api/rides/{id}/leave", rideId), member)).andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/rides/{id}", rideId), first))
                .andExpect(jsonPath("$.viewerRole").value("MEMBER"))
                .andExpect(jsonPath("$.ride.status").value("FULL"))
                .andExpect(jsonPath("$.waitlist.size").value(1));
        mockMvc.perform(authed(get("/api/rides/{id}", rideId), second))
                .andExpect(jsonPath("$.waitlist.viewerPosition").value(1));
        mockMvc.perform(authed(get("/api/notifications"), first))
                .andExpect(jsonPath("$.content[0].type").value("WAITLIST_PROMOTED"));
    }

    @Test
    void freedSeatGoesToTheFirstWaiterWhoseRequestFits() throws Exception {
        TestUser creator = registerStudent("creator");
        TestUser memberA = registerStudent("membera");
        TestUser memberB = registerStudent("memberb");
        TestUser wantsTwo = registerStudent("wantstwo");
        TestUser wantsOne = registerStudent("wantsone");
        long rideId = createRide(creator, airportRide("09:30", 3));
        join(memberA, rideId);
        join(memberB, rideId);
        queue(wantsTwo, rideId, 2).andExpect(status().isOk());
        queue(wantsOne, rideId, 1).andExpect(status().isOk());

        // One seat frees up: the 2-seat request does not fit and keeps its place, the 1-seat request behind it gets in.
        mockMvc.perform(authed(post("/api/rides/{id}/leave", rideId), memberA)).andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/rides/{id}", rideId), wantsOne))
                .andExpect(jsonPath("$.viewerRole").value("MEMBER"));
        mockMvc.perform(authed(get("/api/rides/{id}", rideId), wantsTwo))
                .andExpect(jsonPath("$.waitlist.viewerPosition").value(1))
                .andExpect(jsonPath("$.waitlist.viewerSeats").value(2));
    }

    @Test
    void waiterWhoNowHasAnOverlappingRideIsSkippedAndRemoved() throws Exception {
        TestUser creator = registerStudent("creator");
        TestUser member = registerStudent("member");
        TestUser busy = registerStudent("busy");
        TestUser next = registerStudent("next");
        long rideId = createRide(creator, airportRide("09:30", 2));
        join(member, rideId);
        queue(busy, rideId).andExpect(status().isOk());
        queue(next, rideId).andExpect(status().isOk());
        // While waiting, "busy" creates their own ride at almost the same time.
        createRide(busy, airportRide("09:45", 3));

        mockMvc.perform(authed(post("/api/rides/{id}/leave", rideId), member)).andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/rides/{id}", rideId), next))
                .andExpect(jsonPath("$.viewerRole").value("MEMBER"))
                .andExpect(jsonPath("$.waitlist.size").value(0));
        mockMvc.perform(authed(get("/api/notifications"), busy))
                .andExpect(jsonPath("$.content[0].type").value("WAITLIST_REMOVED"));
    }

    @Test
    void raisingTheSeatCountPromotesWaiters() throws Exception {
        TestUser creator = registerStudent("creator");
        TestUser member = registerStudent("member");
        TestUser waiter = registerStudent("waiter");
        long rideId = createRide(creator, airportRide("09:30", 2));
        join(member, rideId);
        queue(waiter, rideId).andExpect(status().isOk());

        mockMvc.perform(authed(put("/api/rides/{id}", rideId), creator)
                        .contentType(MediaType.APPLICATION_JSON).content(json(airportRide("09:30", 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ride.occupiedSeats").value(3))
                .andExpect(jsonPath("$.waitlist.size").value(0));
    }

    @Test
    void cancellingTheRideClearsTheQueueAndTellsWaiters() throws Exception {
        TestUser creator = registerStudent("creator");
        TestUser member = registerStudent("member");
        TestUser waiter = registerStudent("waiter");
        long rideId = createRide(creator, airportRide("09:30", 2));
        join(member, rideId);
        queue(waiter, rideId).andExpect(status().isOk());

        mockMvc.perform(authed(post("/api/rides/{id}/cancel", rideId), creator)).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("select count(*) from ride_waitlist", Long.class)).isZero();
        mockMvc.perform(authed(get("/api/notifications"), waiter))
                .andExpect(jsonPath("$.content[0].type").value("WAITLIST_REMOVED"));
    }

    @Test
    void queueRulesAreEnforced() throws Exception {
        TestUser creator = registerStudent("creator");
        TestUser member = registerStudent("member");
        TestUser waiter = registerStudent("waiter");
        long rideId = createRide(creator, airportRide("09:30", 2));

        queue(waiter, rideId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SEATS_AVAILABLE"));
        join(member, rideId);
        queue(member, rideId).andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
        queue(waiter, rideId).andExpect(status().isOk());
        queue(waiter, rideId).andExpect(jsonPath("$.code").value("ALREADY_WAITLISTED"));

        mockMvc.perform(authed(delete("/api/rides/{id}/waitlist", rideId), waiter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitlist.size").value(0));
        mockMvc.perform(authed(delete("/api/rides/{id}/waitlist", rideId), waiter))
                .andExpect(jsonPath("$.code").value("NOT_WAITLISTED"));
    }

    /**
     * Three members leave at the same instant: each leave locks the ride, frees a
     * seat and promotes the next waiter before the next leave can run, so exactly
     * the first three waiters get in, in queue order, and the ride never over-books.
     */
    @Test
    void simultaneousLeavesPromoteWaitersInQueueOrder() throws Exception {
        TestUser creator = registerStudent("creator");
        long rideId = createRide(creator, airportRide("09:30", 4));
        List<TestUser> leavers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TestUser m = registerStudent("leaver" + i);
            join(m, rideId);
            leavers.add(m);
        }
        List<TestUser> waiters = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            TestUser w = registerStudent("waiter" + i);
            queue(w, rideId).andExpect(status().isOk());
            waiters.add(w);
        }

        ExecutorService pool = Executors.newFixedThreadPool(leavers.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (TestUser leaver : leavers) {
            futures.add(pool.submit(() -> {
                start.await();
                return participationService.leave(rideId, leaver.id());
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(rideRepository.findById(rideId).orElseThrow().getOccupiedSeats()).isEqualTo(4);
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(authed(get("/api/rides/{id}", rideId), waiters.get(i)))
                    .andExpect(jsonPath("$.viewerRole").value("MEMBER"));
        }
        JsonNode last = read(mockMvc.perform(authed(get("/api/rides/{id}", rideId), waiters.get(3))).andReturn());
        assertThat(last.at("/waitlist/viewerPosition").asInt()).isEqualTo(1);
        Long seatSum = jdbcTemplate.queryForObject(
                "select sum(seats_booked) from ride_participants where ride_id = ?", Long.class, rideId);
        assertThat(seatSum).isEqualTo(4);
    }

    private org.springframework.test.web.servlet.ResultActions queue(TestUser user, long rideId) throws Exception {
        return queue(user, rideId, 1);
    }

    private org.springframework.test.web.servlet.ResultActions queue(TestUser user, long rideId, int seats)
            throws Exception {
        return mockMvc.perform(authed(post("/api/rides/{id}/waitlist", rideId), user)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("seats", seats))));
    }
}
