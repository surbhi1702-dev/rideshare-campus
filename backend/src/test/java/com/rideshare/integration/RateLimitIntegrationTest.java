package com.rideshare.integration;

import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.auth-per-minute=3",
        "app.rate-limit.join-per-minute=2"})
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    @Test
    void repeatedFailedLoginsFromOneIpGet429WithRetryAfter() throws Exception {
        String body = json(Map.of("email", "nobody@iitbbs.ac.in", "password", "Wrong1234"));
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login").with(ip("10.0.0.1"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login").with(ip("10.0.0.1"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        // Another client is unaffected.
        mockMvc.perform(post("/api/auth/login").with(ip("10.0.0.2"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void joinRequestsAreLimitedPerUser() throws Exception {
        // Two registrations stay within the per-IP auth limit of 3.
        TestUser creator = registerStudent("creator");
        TestUser spammer = registerStudent("spammer");
        long rideId = createRide(creator, airportRide("09:30", 4));

        mockMvc.perform(authed(post("/api/rides/{id}/join", rideId), spammer)).andExpect(status().isOk());
        mockMvc.perform(authed(post("/api/rides/{id}/join", rideId), spammer))
                .andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
        mockMvc.perform(authed(post("/api/rides/{id}/join", rideId), spammer))
                .andExpect(status().isTooManyRequests());
        // Other endpoints are not limited.
        mockMvc.perform(authed(post("/api/rides/{id}/leave", rideId), spammer)).andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor ip(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
