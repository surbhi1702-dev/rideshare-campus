package com.rideshare.auth.dto;

import com.rideshare.user.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "Rahul Sharma") @NotBlank @Size(min = 2, max = 100) String name,
        @Schema(example = "rahul.sharma@iitbbs.ac.in") @NotBlank @Email @Size(max = 255) String email,
        @Schema(example = "Secret123") @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE)
        String password,
        @Schema(example = "9876543210", description = "Optional; shown only to members of the same ride")
        @Pattern(regexp = ValidationPatterns.PHONE, message = "must be 10-15 digits, optionally starting with +")
        String phoneNumber
) {
}
