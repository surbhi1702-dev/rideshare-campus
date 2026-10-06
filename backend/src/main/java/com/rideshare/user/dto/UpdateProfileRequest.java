package com.rideshare.user.dto;

import com.rideshare.user.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(min = 2, max = 100) String name,
        @Pattern(regexp = ValidationPatterns.PHONE, message = "must be 10-15 digits, optionally starting with +")
        String phoneNumber
) {
}
