package com.rideshare.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideshare.user.Role;
import com.rideshare.user.User;
import com.rideshare.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the full application against PostgreSQL (see application-test.yml) and
 * wipes all tables before each test so tests are independent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Tests register many users from one IP, so rate limiting is off. Set here rather than
// only in application-test.yml because test properties also beat environment variables
// and external config files on a developer's machine. RateLimitIntegrationTest turns it on.
@TestPropertySource(properties = "app.rate-limit.enabled=false")
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "Secret123";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected Clock clock;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE idempotency_keys, refresh_tokens, ride_waitlist, notifications, reports, "
                + "user_blocks, ride_participants, rides, users RESTART IDENTITY CASCADE");
    }

    // ---------------------------------------------------------------- users

    /** Registers a student through the API and returns their bearer header value. */
    protected TestUser registerStudent(String handle) throws Exception {
        Map<String, Object> body = Map.of(
                "name", capitalize(handle) + " Tester",
                "email", handle + "@iitbbs.ac.in",
                "password", PASSWORD,
                "phoneNumber", "98765" + String.format("%05d", Math.abs(handle.hashCode()) % 100000));
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = read(result);
        return new TestUser(node.at("/user/id").asLong(), handle + "@iitbbs.ac.in",
                "Bearer " + node.get("accessToken").asText());
    }

    /** Admins cannot self-register, so tests insert one directly and log in. */
    protected TestUser createAdmin() throws Exception {
        User admin = userRepository.save(new User("Admin User", "admin@iitbbs.ac.in",
                passwordEncoder.encode(PASSWORD), null, Role.ADMIN));
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "admin@iitbbs.ac.in", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return new TestUser(admin.getId(), admin.getEmail(), "Bearer " + read(result).get("accessToken").asText());
    }

    // ---------------------------------------------------------------- rides

    protected LocalDate tomorrow() {
        return LocalDate.now(clock).plusDays(1);
    }

    /** Ride body from campus to the airport tomorrow at the given time. */
    protected Map<String, Object> airportRide(String time, int totalSeats) {
        return rideBody("IIT Bhubaneswar", 20.1484, 85.6706, "Airport", 20.2444, 85.8178, tomorrow(), time,
                totalSeats, "600.00");
    }

    protected Map<String, Object> rideBody(String source, double sLat, double sLng, String destination, double dLat,
                                           double dLng, LocalDate date, String time, int totalSeats, String fare) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceName", source);
        body.put("sourceLatitude", sLat);
        body.put("sourceLongitude", sLng);
        body.put("destinationName", destination);
        body.put("destinationLatitude", dLat);
        body.put("destinationLongitude", dLng);
        body.put("departureDate", date.toString());
        body.put("departureTime", time);
        body.put("totalSeats", totalSeats);
        body.put("totalFare", fare);
        return body;
    }

    protected long createRide(TestUser user, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(authed(post("/api/rides"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).at("/ride/id").asLong();
    }

    protected void join(TestUser user, long rideId) throws Exception {
        mockMvc.perform(authed(post("/api/rides/{id}/join", rideId), user))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- helpers

    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, TestUser user) {
        return builder.header(HttpHeaders.AUTHORIZATION, user.bearer());
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    public record TestUser(Long id, String email, String bearer) {
    }
}
