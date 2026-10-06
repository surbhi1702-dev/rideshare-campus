package com.rideshare.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Strongly typed view of the {@code app.*} configuration tree.
 * Values come from application.yml, which in turn reads environment variables.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String timeZone,
        @Valid @NotNull Auth auth,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Admin admin,
        @Valid @NotNull Ride ride
) {

    public record Auth(@NotEmpty List<String> allowedEmailDomains) {
    }

    public record Jwt(@NotBlank String secret, @Positive long expirationMinutes) {
    }

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    public record Admin(String email, String password, String name) {
        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }

    public record Ride(
            @Min(2) int maxSeats,
            @Positive int maxDaysAhead,
            @Positive double minTripDistanceKm,
            @Positive int overlapWindowMinutes,
            @Positive int startEarlyWindowMinutes,
            @Positive int lockTimeoutMs
    ) {
    }
}
