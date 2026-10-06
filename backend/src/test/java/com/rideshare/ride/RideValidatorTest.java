package com.rideshare.ride;

import com.rideshare.common.exception.ErrorCode;
import com.rideshare.common.exception.InvalidRequestException;
import com.rideshare.config.AppProperties;
import com.rideshare.matching.geo.HaversineDistanceCalculator;
import com.rideshare.ride.dto.CreateRideRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RideValidatorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 3, 1, 12, 0);

    static AppProperties properties() {
        return new AppProperties("Asia/Kolkata",
                new AppProperties.Auth(List.of("iitbbs.ac.in")),
                new AppProperties.Jwt("unused", 60),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Admin(null, null, null),
                new AppProperties.Ride(7, 60, 0.5, 90, 60, 3000));
    }

    private final RideValidator validator = new RideValidator(
            Clock.fixed(ZonedDateTime.of(NOW, ZONE).toInstant(), ZONE), properties(), new HaversineDistanceCalculator());

    private static CreateRideRequest request(LocalDate date, LocalTime time, int seats, Integer creatorSeats,
                                             double destLat, double destLng) {
        return new CreateRideRequest(" Campus ", 20.1484, 85.6706, "Airport", destLat, destLng, date, time, seats,
                creatorSeats, new BigDecimal("600"), "  ");
    }

    @Test
    void validRequestIsNormalised() {
        RideDetails details = validator.validateCreate(request(NOW.toLocalDate().plusDays(1), LocalTime.of(9, 30), 4,
                null, 20.2444, 85.8178));

        assertThat(details.sourceName()).isEqualTo("Campus");
        assertThat(details.departureAt()).isEqualTo(LocalDateTime.of(2030, 3, 2, 9, 30));
        assertThat(details.totalFare()).isEqualByComparingTo("600.00");
        assertThat(details.notes()).isNull();
    }

    @Test
    void departureInThePastIsRejected() {
        assertThatThrownBy(() -> validator.validateCreate(request(NOW.toLocalDate(), LocalTime.of(11, 59), 4, 1,
                20.2444, 85.8178)))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_DEPARTURE_TIME);
    }

    @Test
    void departureTooFarAheadIsRejected() {
        assertThatThrownBy(() -> validator.validateCreate(request(NOW.toLocalDate().plusDays(61), LocalTime.NOON, 4, 1,
                20.2444, 85.8178)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_DEPARTURE_TIME);
    }

    @Test
    void creatorMustLeaveAtLeastOneSeat() {
        assertThatThrownBy(() -> validator.validateCreate(request(NOW.toLocalDate().plusDays(1), LocalTime.NOON, 3, 3,
                20.2444, 85.8178)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_SEAT_COUNT);
    }

    @Test
    void seatsAboveConfiguredMaximumAreRejected() {
        assertThatThrownBy(() -> validator.validateCreate(request(NOW.toLocalDate().plusDays(1), LocalTime.NOON, 8, 1,
                20.2444, 85.8178)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_SEAT_COUNT);
    }

    @Test
    void sourceAndDestinationTooCloseIsRejected() {
        assertThatThrownBy(() -> validator.validateCreate(request(NOW.toLocalDate().plusDays(1), LocalTime.NOON, 4, 1,
                20.1486, 85.6707)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ROUTE);
    }
}
