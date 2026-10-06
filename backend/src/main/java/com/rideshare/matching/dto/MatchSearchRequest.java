package com.rideshare.matching.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/** Ad-hoc "find me a ride" query, bound from request parameters. */
public record MatchSearchRequest(
        @Schema(example = "20.1484") @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double sourceLatitude,
        @Schema(example = "85.6706") @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double sourceLongitude,
        @Schema(example = "20.2444") @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double destinationLatitude,
        @Schema(example = "85.8178") @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double destinationLongitude,
        @Schema(example = "2026-10-05") @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @Schema(example = "09:30", type = "string") @NotNull @DateTimeFormat(pattern = "HH:mm")
        @JsonFormat(pattern = "HH:mm") LocalTime time,
        @Schema(example = "1") @Min(1) @Max(9) Integer seats
) {
}
