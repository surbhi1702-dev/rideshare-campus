package com.rideshare.integration;

import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminIntegrationTest extends AbstractIntegrationTest {

    @Test
    void studentsCannotUseAdminEndpoints() throws Exception {
        TestUser student = registerStudent("aarav");

        mockMvc.perform(authed(get("/api/admin/stats"), student))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(authed(patch("/api/admin/users/{id}/deactivate", student.id()), student))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSeesStatsUsersAndRides() throws Exception {
        TestUser admin = createAdmin();
        TestUser a = registerStudent("aarav");
        TestUser b = registerStudent("priya");
        long ride = createRide(a, airportRide("09:00", 4));
        join(b, ride);
        long cancelled = createRide(b, airportRide("18:00", 4));
        mockMvc.perform(authed(post("/api/rides/{id}/cancel", cancelled), b)).andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/admin/stats"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(3))
                .andExpect(jsonPath("$.totalRides").value(2))
                .andExpect(jsonPath("$.ridesByStatus.OPEN").value(1))
                .andExpect(jsonPath("$.ridesByStatus.CANCELLED").value(1))
                .andExpect(jsonPath("$.upcomingActiveRides").value(1));

        mockMvc.perform(authed(get("/api/admin/rides").param("status", "CANCELLED"), admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ride.id").value(cancelled))
                .andExpect(jsonPath("$.content[0].creatorEmail").value("priya@iitbbs.ac.in"));

        mockMvc.perform(authed(get("/api/admin/users").param("query", "priya"), admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    void deactivatedUserIsLockedOutImmediately() throws Exception {
        TestUser admin = createAdmin();
        TestUser student = registerStudent("aarav");

        mockMvc.perform(authed(patch("/api/admin/users/{id}/deactivate", student.id()), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Existing token stops working at once...
        mockMvc.perform(authed(get("/api/users/me"), student))
                .andExpect(status().isUnauthorized());
        // ...and logging in again is refused.
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", student.email(), "password", PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

        mockMvc.perform(authed(patch("/api/admin/users/{id}/deactivate", admin.id()), admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanResolveReports() throws Exception {
        TestUser admin = createAdmin();
        TestUser a = registerStudent("aarav");
        TestUser b = registerStudent("priya");
        long ride = createRide(a, airportRide("09:00", 4));
        join(b, ride);
        long reportId = read(mockMvc.perform(authed(post("/api/reports"), b).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reportedUserId", a.id(), "rideId", ride, "reason", "FARE_DISPUTE"))))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();

        mockMvc.perform(authed(get("/api/admin/reports").param("status", "OPEN"), admin))
                .andExpect(jsonPath("$.content[0].id").value(reportId))
                .andExpect(jsonPath("$.content[0].reportedUserEmail").value("aarav@iitbbs.ac.in"));

        mockMvc.perform(authed(patch("/api/admin/reports/{id}", reportId), admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "RESOLVED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
    }
}
