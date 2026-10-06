package com.rideshare.safety;

import com.rideshare.safety.dto.BlockedUserResponse;
import com.rideshare.safety.dto.CreateReportRequest;
import com.rideshare.safety.dto.ReportSubmittedResponse;
import com.rideshare.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Safety", description = "Block and report other students")
public class SafetyController {

    private final SafetyService safetyService;

    public SafetyController(SafetyService safetyService) {
        this.safetyService = safetyService;
    }

    @GetMapping("/api/users/me/blocks")
    @Operation(summary = "Users I have blocked")
    public List<BlockedUserResponse> blocked(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return safetyService.listBlocked(currentUser.id());
    }

    @PostMapping("/api/users/me/blocks/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Block a user",
            description = "Blocked users never see each other's rides in search/matching and cannot join a ride "
                    + "the other is in. 409 ALREADY_BLOCKED.")
    public BlockedUserResponse block(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                     @PathVariable Long userId) {
        return safetyService.block(currentUser.id(), userId);
    }

    @DeleteMapping("/api/users/me/blocks/{userId}")
    @Operation(summary = "Unblock a user")
    public ResponseEntity<Void> unblock(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                        @PathVariable Long userId) {
        safetyService.unblock(currentUser.id(), userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/reports")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Report a student you shared a ride with", description = "Reviewed by admins.")
    public ReportSubmittedResponse report(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                          @Valid @RequestBody CreateReportRequest request) {
        return safetyService.report(currentUser.id(), request);
    }
}
