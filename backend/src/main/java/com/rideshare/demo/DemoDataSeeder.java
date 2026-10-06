package com.rideshare.demo;

import com.rideshare.auth.AuthService;
import com.rideshare.auth.dto.RegisterRequest;
import com.rideshare.ride.RideParticipationService;
import com.rideshare.ride.RideRepository;
import com.rideshare.ride.RideService;
import com.rideshare.ride.dto.CreateRideRequest;
import com.rideshare.ride.dto.JoinRideRequest;
import com.rideshare.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Sample students and rides for demos. Active ONLY with the "demo" Spring profile
 * (SPRING_PROFILES_ACTIVE=demo) and only when the database has no rides yet.
 * Goes through the real services, so all business rules apply to the sample data.
 */
@Component
@Profile("demo")
@Order(10)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final double CAMPUS_LAT = 20.1484;
    private static final double CAMPUS_LNG = 85.6706;

    private final AuthService authService;
    private final RideService rideService;
    private final RideParticipationService participationService;
    private final UserRepository userRepository;
    private final RideRepository rideRepository;
    private final Clock clock;
    private final String demoPassword;
    private final String demoDomain;

    public DemoDataSeeder(AuthService authService, RideService rideService,
                          RideParticipationService participationService, UserRepository userRepository,
                          RideRepository rideRepository, Clock clock,
                          @Value("${app.demo.password}") String demoPassword,
                          @Value("${app.demo.email-domain}") String demoDomain) {
        this.authService = authService;
        this.rideService = rideService;
        this.participationService = participationService;
        this.userRepository = userRepository;
        this.rideRepository = rideRepository;
        this.clock = clock;
        this.demoPassword = demoPassword;
        this.demoDomain = demoDomain;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (rideRepository.count() > 0) {
            log.info("Demo data skipped: rides already exist");
            return;
        }
        List<Long> ids = List.of(
                register("Aarav Mishra", "aarav", "9000000001"),
                register("Priya Nayak", "priya", "9000000002"),
                register("Rohan Das", "rohan", "9000000003"),
                register("Sneha Patel", "sneha", "9000000004"),
                register("Kabir Singh", "kabir", "9000000005"));

        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);

        // Aarav -> Airport tomorrow 09:00, Priya joins (2/4)
        Long airport = rideService.create(ids.get(0), ride("IIT Bhubaneswar (Argul Campus)", CAMPUS_LAT, CAMPUS_LNG,
                "Biju Patnaik International Airport", 20.2444, 85.8178, tomorrow, LocalTime.of(9, 0), 4,
                "900.00", "Flight at 11:30. Meeting at main gate.")).ride().id();
        participationService.join(airport, ids.get(1), new JoinRideRequest(1, null));

        // Rohan -> Railway station tomorrow 17:30 (1/4)
        rideService.create(ids.get(2), ride("IIT Bhubaneswar (Argul Campus)", CAMPUS_LAT, CAMPUS_LNG,
                "Bhubaneswar Railway Station", 20.2667, 85.8434, tomorrow, LocalTime.of(17, 30), 4,
                "750.00", "Train at 19:10."));

        // Sneha -> Station from hostel side, 17:50 - a close match for Rohan's ride
        rideService.create(ids.get(3), ride("Campus Hostel Gate", 20.1502, 85.6689,
                "Master Canteen Square", 20.2687, 85.8428, tomorrow, LocalTime.of(17, 50), 3,
                "700.00", null));

        // Kabir -> Patia day after tomorrow 10:00 (1/5)
        rideService.create(ids.get(4), ride("IIT Bhubaneswar (Argul Campus)", CAMPUS_LAT, CAMPUS_LNG,
                "Patia / KIIT Square", 20.3538, 85.8190, tomorrow.plusDays(1), LocalTime.of(10, 0), 5,
                "1000.00", "Going for a hackathon."));

        log.info("Demo data created: 5 students (<name>@{}) and 4 rides", demoDomain);
    }

    private Long register(String name, String handle, String phone) {
        String email = handle + "@" + demoDomain;
        return userRepository.findByEmailIgnoreCase(email)
                .map(user -> user.getId())
                .orElseGet(() -> authService.register(new RegisterRequest(name, email, demoPassword, phone)).user().id());
    }

    private static CreateRideRequest ride(String source, double sLat, double sLng, String destination, double dLat,
                                          double dLng, LocalDate date, LocalTime time, int seats, String fare,
                                          String notes) {
        return new CreateRideRequest(source, sLat, sLng, destination, dLat, dLng, date, time, seats, 1,
                new BigDecimal(fare), notes);
    }
}
