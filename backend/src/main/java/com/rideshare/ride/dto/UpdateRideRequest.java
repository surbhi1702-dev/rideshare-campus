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

/** Full replacement of the editable ride attributes (PUT semantics). */
public record UpdateRideRequest(
        @NotBlank @Size(max = 120) String sourceName,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double sourceLatitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double sourceLongitude,
        @NotBlank @Size(max = 120) String destinationName,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double destinationLatitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double destinationLongitude,
        @NotNull LocalDate departureDate,
        @Schema(type = "string", example = "09:30") @NotNull @JsonFormat(pattern = "HH:mm") LocalTime departureTime,
        @NotNull @Min(2) @Max(10) Integer totalSeats,
        @NotNull @DecimalMin(value = "0.0") @Digits(integer = 6, fraction = 2) BigDecimal totalFare,
        @Size(max = 500) String notes
) {
}
