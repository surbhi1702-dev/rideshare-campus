package com.rideshare.ride.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record CreateRideRequest(
        @Schema(example = "IIT Bhubaneswar (Argul Campus)") @NotBlank @Size(max = 120) String sourceName,
        @Schema(example = "20.1484") @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double sourceLatitude,
        @Schema(example = "85.6706") @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double sourceLongitude,
        @Schema(example = "Biju Patnaik International Airport") @NotBlank @Size(max = 120) String destinationName,
        @Schema(example = "20.2444") @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double destinationLatitude,
        @Schema(example = "85.8178") @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double destinationLongitude,
        @Schema(example = "2026-10-05", description = "Institute local date") @NotNull LocalDate departureDate,
        @Schema(example = "09:30", type = "string", description = "Institute local time, HH:mm")
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime departureTime,
        @Schema(example = "4", description = "Total passenger capacity including you") @NotNull @Min(2) @Max(10)
        Integer totalSeats,
        @Schema(example = "1", description = "Seats you need yourself (default 1)") @Min(1) @Max(9)
        Integer seatsForCreator,
        @Schema(example = "800.00", description = "Expected total cab fare in INR") @NotNull
        @DecimalMin(value = "0.0") @Digits(integer = 6, fraction = 2) BigDecimal totalFare,
        @Size(max = 500) String notes
) {
}
