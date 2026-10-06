package com.rideshare.location;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** Frequently used pickup/drop points, configured per campus in application.yml. */
@Validated
@ConfigurationProperties(prefix = "app")
public record PlacesProperties(@Valid List<Place> places) {

    public PlacesProperties {
        places = places == null ? List.of() : List.copyOf(places);
    }

    public record Place(
            @NotBlank String name,
            @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") double longitude
    ) {
    }
}
