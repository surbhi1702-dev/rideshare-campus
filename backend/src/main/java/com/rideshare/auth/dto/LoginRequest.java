package com.rideshare.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "rahul.sharma@iitbbs.ac.in") @NotBlank String email,
        @Schema(example = "Secret123") @NotBlank String password
) {
}
