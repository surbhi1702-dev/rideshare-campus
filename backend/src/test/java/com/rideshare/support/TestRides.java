package com.rideshare.support;

import com.rideshare.matching.geo.GeoPoint;
import com.rideshare.ride.Ride;
import com.rideshare.ride.RideDetails;
import com.rideshare.user.Role;
import com.rideshare.user.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Builders for in-memory entities used by unit tests (no database). */
public final class TestRides {

    public static final GeoPoint CAMPUS = new GeoPoint(20.1484, 85.6706);
    public static final GeoPoint AIRPORT = new GeoPoint(20.2444, 85.8178);
    public static final GeoPoint RAILWAY_STATION = new GeoPoint(20.2667, 85.8434);
    public static final GeoPoint CUTTACK = new GeoPoint(20.4644, 85.8990);

    private TestRides() {
    }

    public static User user(long id, String name) {
        User user = new User(name, name.toLowerCase().replace(' ', '.') + "@iitbbs.ac.in", "hash", null, Role.STUDENT);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    public static Ride ride(long id, GeoPoint from, GeoPoint to, LocalDateTime departure, int totalSeats,
                            int occupiedSeats) {
        RideDetails details = new RideDetails("from", from, "to", to, departure, totalSeats,
                new BigDecimal("800.00"), null);
        Ride ride = new Ride(user(1000 + id, "Creator " + id), details, occupiedSeats);
        ReflectionTestUtils.setField(ride, "id", id);
        return ride;
    }
}
