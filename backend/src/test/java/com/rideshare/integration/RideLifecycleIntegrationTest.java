package com.rideshare.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end: create -> view -> join -> group/fare -> notifications -> leave/cancel. */
class RideLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Test
    void createRideMakesCreatorTheFirstParticipant() throws Exception {
        TestUser creator = registerStudent("aarav");

        mockMvc.perform(authed(post("/api/rides"), creator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(airportRide("09:30", 4))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/rides/")))
                .andExpect(jsonPath("$.ride.status").value("OPEN"))
                .andExpect(jsonPath("$.ride.occupiedSeats").value(1))
                .andExpect(jsonPath("$.ride.availableSeats").value(3))
                .andExpect(jsonPath("$.viewerRole").value("CREATOR"))
                .andExpect(jsonPath("$.participants", hasSize(1)))
                .andExpect(jsonPath("$.fareSplit.yourShare").value(600.00))
                .andExpect(jsonPath("$.actions.canCancel").value(true))
                .andExpect(jsonPath("$.actions.canJoin").value(false));
    }

    @Test
    void invalidRideInputIsRejected() throws Exception {
        TestUser creator = registerStudent("aarav");
        Map<String, Object> past = rideBody("Campus", 20.1484, 85.6706, "Airport", 20.2444, 85.8178,
                tomorrow().minusDays(2), "09:00", 4, "600");
        Map<String, Object> negativeFare = airportRide("09:00", 4);
        negativeFare.put("totalFare", "-5");
        Map<String, Object> zeroSeats = airportRide("09:00", 4);
        zeroSeats.put("totalSeats", 0);

        mockMvc.perform(authed(post("/api/rides"), creator).contentType(MediaType.APPLICATION_JSON).content(json(past)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DEPARTURE_TIME"));
        mockMvc.perform(authed(post("/api/rides"), creator).contentType(MediaType.APPLICATION_JSON).content(json(negativeFare)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("totalFare"));
        mockMvc.perform(authed(post("/api/rides"), creator).contentType(MediaType.APPLICATION_JSON).content(json(zeroSeats)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("totalSeats"));
    }

    @Test
    void membersSeeGroupDetailsButOutsidersDoNot() throws Exception {
        TestUser creator = registerStudent("aarav");
        TestUser member = registerStudent("priya");
        TestUser outsider = registerStudent("rohan");
        long rideId = createRide(creator, airportRide("09:30", 4));

        mockMvc.perform(authed(post("/api/rides/{id}/join", rideId), member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ride.occupiedSeats").value(2))
                .andExpect(jsonPath("$.viewerRole").value("MEMBER"))
                .andExpect(jsonPath("$.participants", hasSize(2)))
                .andExpect(jsonPath("$.participants[0].phoneNumber").isNotEmpty())
                .andExpect(jsonPath("$.fareSplit.sharePerSeat").value(300.00))
                .andExpect(jsonPath("$.fareSplit.yourShare").value(300.00));

        mockMvc.perform(authed(get("/api/rides/{id}", rideId), outsider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.participants", hasSize(0)))
                .andExpect(jsonPath("$.fareSplit").doesNotExist())
                .andExpect(jsonPath("$.estimatedShareIfJoined").value(200.00))
                .andExpect(jsonPath("$.ride.creator.displayName").value("Aarav T."))
                .andExpect(jsonPath("$.actions.canJoin").value(true));
    }

    @Test
    void joiningNotifiesExistingMembersAndNotificationsCanBeRead() throws Exception {
        TestUser creator = registerStudent("aarav");
        TestUser member = registerStudent("priya");
        long rideId = createRide(creator, airportRide("09:30", 4));
        join(member, rideId);

        JsonNode inbox = read(mockMvc.perform(authed(get("/api/notifications"), creator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("RIDE_JOINED"))
                .andExpect(jsonPath("$.content[0].rideId").value(rideId))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andReturn());
        long notificationId = inbox.at("/content/0/id").asLong();

        mockMvc.perform(authed(get("/api/notifications/unread-count"), creator))
                .andExpect(jsonPath("$.unread").value(1));

        // Another student cannot touch someone else's notification.
        mockMvc.perform(authed(patch("/api/notifications/{id}/read", notificationId), member))
                .andExpect(status().isNotFound());

        mockMvc.perform(authed(patch("/api/notifications/{id}/read", notificationId), creator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        mockMvc.perform(authed(get("/api/notifications/unread-count"), creator))
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void duplicateJoinAndCreatorJoinAreRejected() throws Exception {
        TestUser creator = registerStudent("aarav");
        TestUser member = registerStudent("priya");
        long rideId = createRide(creator, airportRide("09:30", 4));
        join(member, rideId);

        mockMvc.perform(authed(post("/api/rides/{id}/join", rideId), member))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
        mockMvc.perform(authed(post("/api/rides/{id}/join", rideId), creator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
    }

    @Test
    void lastSeatMakesRideFullAndFurtherJoinsFail() throws Exception {
        TestUser creator = registerStudent("aarav");
        TestUser a = registerStudent("priya");
        TestUser b = registerStudent("rohan");
        long rideId = createRide(creator, airportRide("09:30", 3));
        join(a, rideId);

        mockMvc.perform(authed(post("/api/rides/{id}/join", rideId), b))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ride.status").value("FULL"))
                .andExpect(jsonPath("$.ride.availableSeats").value(0));

        TestUser late = registerStudent("sneha");
        mockMvc.perform(authed(post("/api/rides/{id}/join", rideId), late))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RIDE_FULL"));
    }

    @Test
    void leavingFreesTheSeatAndReopensTheRide() throws Exception {
        TestUser creator = registerStudent("aarav");
        TestUser a = registerStudent("priya");
        TestUser b = registerStudent("rohan");
        long rideId = createRide(creator, airportRide("09:30", 3));
        join(a, rideId);
        join(b, rideId);

        mockMvc.perform(authed(post("/api/rides/{id}/leave", rideId), a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ride.status").value("OPEN"))
                .andExpect(jsonPath("$.ride.occupiedSeats").value(2))
                .andExpect(jsonPath("$.viewerRole").doesNotExist());

        mockMvc.perform(authed(post("/api/rides/{id}/leave", rideId), a))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_A_PARTICIPANT"));
        mockMvc.perform(authed(post("/api/rides/{id}/leave", rideId), creator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREATOR_CANNOT_LEAVE"));
    }

    @Test
    void onlyCreatorCanCancelAndCancelledRidesCannotBeJoined() throws Exception {
        TestUser creator = registerStudent("aarav");
        TestUser member = registerStudent("priya");
        TestUser outsider = registerStudent("rohan");
        long rideId = createRide(creator, airportRide("09:30", 4));
        join(member, rideId);

        mockMvc.perform(authed(post("/api/rides/{id}/cancel", rideId), member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(authed(post("/api/rides/{id}/cancel", rideId), creator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ride.status").value("CANCELLED"));

        mockMvc.perform(authed(post("/api/rides/{id}/join", rideId), outsider))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RIDE_CANCELLED"));
        mockMvc.perform(authed(post("/api/rides/{id}/cancel", rideId), creator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RIDE_CANCELLED"));

        mockMvc.perform(authed(get("/api/notifications"), member))
                .andExpect(jsonPath("$.content[0].type").value("RIDE_CANCELLED"));
    }

    @Test
    void onlyCreatorCanUpdateAndCapacityCannotDropBelowTakenSeats() throws Exception {
        TestUser creator = registerStudent("aarav");
        TestUser member = registerStudent("priya");
        long rideId = createRide(creator, airportRide("09:30", 4));
        join(member, rideId);

        Map<String, Object> update = airportRide("10:00", 4);
        mockMvc.perform(authed(put("/api/rides/{id}", rideId), member)
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(status().isForbidden());

        mockMvc.perform(authed(put("/api/rides/{id}", rideId), creator)
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ride.departureAt").value(tomorrow() + "T10:00:00"));

        update.put("totalSeats", 2);
        mockMvc.perform(authed(put("/api/rides/{id}", rideId), creator)
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ride.status").value("FULL"));

        update.put("totalSeats", 1);
        mockMvc.perform(authed(put("/api/rides/{id}", rideId), creator)
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(authed(get("/api/notifications"), member))
                .andExpect(jsonPath("$.content[0].type").value("RIDE_UPDATED"));
    }

    @Test
    void studentCannotHoldTwoOverlappingRidesButCanMergeIntoAnother() throws Exception {
        TestUser aarav = registerStudent("aarav");
        TestUser priya = registerStudent("priya");
        long aaravRide = createRide(aarav, airportRide("09:30", 4));
        long priyaRide = createRide(priya, airportRide("09:45", 4));

        // A second ride for the same slot is refused...
        mockMvc.perform(authed(post("/api/rides"), priya).contentType(MediaType.APPLICATION_JSON)
                        .content(json(airportRide("10:00", 4))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OVERLAPPING_RIDE"));

        // ...and so is joining another group at the same time without giving up her own.
        mockMvc.perform(authed(post("/api/rides/{id}/join", aaravRide), priya))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OVERLAPPING_RIDE"));

        // Merge: join Aarav's ride and cancel her own in one transaction.
        mockMvc.perform(authed(post("/api/rides/{id}/join", aaravRide), priya)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("seats", 1, "replaceRideId", priyaRide))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ride.occupiedSeats").value(2));

        mockMvc.perform(authed(get("/api/rides/{id}", priyaRide), priya))
                .andExpect(jsonPath("$.ride.status").value("CANCELLED"));
    }

    @Test
    void myRidesListsCreatedAndJoinedRides() throws Exception {
        TestUser aarav = registerStudent("aarav");
        TestUser priya = registerStudent("priya");
        long airport = createRide(aarav, airportRide("09:30", 4));
        long station = createRide(priya, rideBody("Campus", 20.1484, 85.6706, "Station", 20.2667, 85.8434,
                tomorrow(), "18:00", 4, "500"));
        join(aarav, station);

        mockMvc.perform(authed(get("/api/rides/mine"), aarav))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(airport))
                .andExpect(jsonPath("$.content[1].id").value(station));

        mockMvc.perform(authed(get("/api/rides/mine").param("scope", "past"), aarav))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
