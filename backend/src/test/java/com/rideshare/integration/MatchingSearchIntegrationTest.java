package com.rideshare.integration;

import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchingSearchIntegrationTest extends AbstractIntegrationTest {

    private MockHttpServletRequestBuilder searchCampusToAirport(String time) {
        return get("/api/rides/search")
                .param("sourceLatitude", "20.1484").param("sourceLongitude", "85.6706")
                .param("destinationLatitude", "20.2444").param("destinationLongitude", "85.8178")
                .param("date", tomorrow().toString()).param("time", time).param("seats", "1");
    }

    @Test
    void searchRanksNearbyRidesAndExcludesFarOrOffTimeOnes() throws Exception {
        TestUser a = registerStudent("aarav");
        TestUser b = registerStudent("priya");
        TestUser c = registerStudent("rohan");
        TestUser d = registerStudent("sneha");
        TestUser seeker = registerStudent("kabir");

        long exact = createRide(a, airportRide("09:00", 4));
        // Hostel gate (~0.27 km from campus point) to the railway station (~3.6 km from the airport), 20 min later.
        long nearby = createRide(b, rideBody("Hostel Gate", 20.1502, 85.6689, "Railway Station", 20.2667, 85.8434,
                tomorrow(), "09:20", 4, "700"));
        // Same route but 3 hours later: outside the 60 minute window.
        createRide(c, airportRide("12:00", 4));
        // Same time but to Cuttack: destination far away.
        createRide(d, rideBody("Campus", 20.1484, 85.6706, "Cuttack", 20.4644, 85.8990, tomorrow(), "09:00", 4,
                "1200"));

        mockMvc.perform(authed(searchCampusToAirport("09:00"), seeker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].ride.id").value(exact))
                .andExpect(jsonPath("$[0].match.pickupDistanceKm").value(0.0))
                .andExpect(jsonPath("$[0].estimatedShareIfJoined").value(300.00))
                .andExpect(jsonPath("$[1].ride.id").value(nearby))
                .andExpect(jsonPath("$[1].match.timeDifferenceMinutes").value(20));
    }

    @Test
    void searchNeverReturnsMyOwnOrFullRides() throws Exception {
        TestUser a = registerStudent("aarav");
        TestUser b = registerStudent("priya");
        TestUser seeker = registerStudent("kabir");
        createRide(seeker, airportRide("09:00", 4));
        long full = createRide(a, airportRide("09:10", 2));
        join(b, full);

        mockMvc.perform(authed(searchCampusToAirport("09:00"), seeker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void matchesForMyRideSuggestOtherRidesAndAreMembersOnly() throws Exception {
        TestUser a = registerStudent("aarav");
        TestUser b = registerStudent("priya");
        TestUser outsider = registerStudent("rohan");
        long mine = createRide(a, airportRide("09:00", 4));
        long other = createRide(b, airportRide("09:15", 4));

        mockMvc.perform(authed(get("/api/rides/{id}/matches", mine), a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ride.id").value(other))
                .andExpect(jsonPath("$[0].match.compatibilityPercent").isNumber());

        mockMvc.perform(authed(get("/api/rides/{id}/matches", mine), outsider))
                .andExpect(status().isForbidden());
    }

    @Test
    void postingASimilarRideNotifiesTheOwnerOfTheExistingRide() throws Exception {
        TestUser a = registerStudent("aarav");
        TestUser b = registerStudent("priya");
        createRide(a, airportRide("09:00", 4));
        long newRide = createRide(b, airportRide("09:20", 4));

        mockMvc.perform(authed(get("/api/notifications"), a))
                .andExpect(jsonPath("$.content[0].type").value("MATCH_SUGGESTION"))
                .andExpect(jsonPath("$.content[0].rideId").value(newRide));
    }

    @Test
    void browseFiltersByTextDateAndSeats() throws Exception {
        TestUser a = registerStudent("aarav");
        TestUser b = registerStudent("priya");
        TestUser viewer = registerStudent("kabir");
        createRide(a, airportRide("09:00", 4));
        createRide(b, rideBody("Campus", 20.1484, 85.6706, "Railway Station", 20.2667, 85.8434, tomorrow(), "18:00",
                2, "500"));

        mockMvc.perform(authed(get("/api/rides").param("destination", "air"), viewer))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].destinationName").value("Airport"));
        mockMvc.perform(authed(get("/api/rides").param("date", tomorrow().toString())
                        .param("fromTime", "12:00").param("toTime", "23:00"), viewer))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].destinationName").value("Railway Station"));
        mockMvc.perform(authed(get("/api/rides").param("minSeats", "2"), viewer))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(authed(get("/api/rides").param("destination", "100%_"), viewer))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void blockedUsersNeverSeeOrJoinEachOthersRides() throws Exception {
        TestUser a = registerStudent("aarav");
        TestUser b = registerStudent("priya");
        long ride = createRide(a, airportRide("09:00", 4));

        mockMvc.perform(authed(post("/api/users/me/blocks/{id}", b.id()), a))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(searchCampusToAirport("09:00"), b))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(authed(get("/api/rides"), b))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(authed(post("/api/rides/{id}/join", ride), b))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INTERACTION_BLOCKED"))
                .andExpect(jsonPath("$.message").value("You can't join this ride"));

        mockMvc.perform(authed(post("/api/users/me/blocks/{id}", b.id()), a))
                .andExpect(status().isConflict());
        mockMvc.perform(authed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/users/me/blocks/{id}", b.id()), a))
                .andExpect(status().isNoContent());
        mockMvc.perform(authed(post("/api/rides/{id}/join", ride), b))
                .andExpect(status().isOk());
    }

    @Test
    void reportsRequireASharedRide() throws Exception {
        TestUser a = registerStudent("aarav");
        TestUser b = registerStudent("priya");
        TestUser stranger = registerStudent("rohan");
        long ride = createRide(a, airportRide("09:00", 4));
        join(b, ride);

        mockMvc.perform(authed(post("/api/reports"), b).contentType(MediaType.APPLICATION_JSON)
                        .content(json(java.util.Map.of("reportedUserId", a.id(), "rideId", ride, "reason", "NO_SHOW"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(authed(post("/api/reports"), stranger).contentType(MediaType.APPLICATION_JSON)
                        .content(json(java.util.Map.of("reportedUserId", a.id(), "reason", "HARASSMENT"))))
                .andExpect(status().isForbidden());
    }
}
