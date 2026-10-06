package com.rideshare.matching;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tunables of the matching engine (MAX_PICKUP_DISTANCE_KM, MAX_DESTINATION_DISTANCE_KM,
 * MAX_TIME_DIFFERENCE_MINUTES, MATCH_WEIGHT_* ...). The three maxima are both hard
 * cut-offs and the normalisation constants of the score.
 */
@Validated
@ConfigurationProperties(prefix = "app.matching")
public record MatchingProperties(
        @Positive double maxPickupDistanceKm,
        @Positive double maxDestinationDistanceKm,
        @Positive int maxTimeDifferenceMinutes,
        @Positive int maxResults,
        @Positive int suggestionNotificationLimit,
        @Valid @NotNull Weights weights
) {

    public record Weights(
            @DecimalMin("0.0") double pickup,
            @DecimalMin("0.0") double destination,
            @DecimalMin("0.0") double time,
            @DecimalMin("0.0") double group
    ) {

        public Weights {
            if (pickup + destination + time + group <= 0) {
                throw new IllegalArgumentException("At least one matching weight must be positive");
            }
        }

        public double sum() {
            return pickup + destination + time + group;
        }
    }
}
