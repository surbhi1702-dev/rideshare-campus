package com.rideshare.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape returned by every endpoint.
 */
@Schema(name = "ApiError", description = "Consistent error body for all failures")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        @Schema(description = "Stable machine readable code, e.g. RIDE_FULL") String code,
        String message,
        String path,
        List<FieldViolation> fieldErrors
) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(ErrorCode code, String message, String path) {
        return of(code, message, path, List.of());
    }

    public static ApiError of(ErrorCode code, String message, String path, List<FieldViolation> fieldErrors) {
        return new ApiError(Instant.now(), code.status().value(), code.status().getReasonPhrase(),
                code.name(), message, path, fieldErrors);
    }
}
