package com.rideshare.admin;

import com.rideshare.admin.dto.AdminRideResponse;
import com.rideshare.admin.dto.AdminStatsResponse;
import com.rideshare.admin.dto.AdminUserResponse;
import com.rideshare.admin.dto.UpdateReportStatusRequest;
import com.rideshare.common.dto.PageResponse;
import com.rideshare.common.dto.Paging;
import com.rideshare.ride.RideStatus;
import com.rideshare.safety.ReportStatus;
import com.rideshare.safety.dto.ReportResponse;
import com.rideshare.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Moderation endpoints. Secured twice: URL rule in SecurityConfig and @PreAuthorize here. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "ADMIN role only (403 for students)")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    @Operation(summary = "Platform statistics")
    public AdminStatsResponse stats() {
        return adminService.stats();
    }

    @GetMapping("/users")
    @Operation(summary = "List/search users")
    public PageResponse<AdminUserResponse> users(@RequestParam(required = false) String query,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return adminService.users(query, Paging.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id"))));
    }

    @PatchMapping("/users/{id}/deactivate")
    @Operation(summary = "Deactivate a user (they are signed out immediately)")
    public AdminUserResponse deactivate(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id) {
        return adminService.setActive(admin.id(), id, false);
    }

    @PatchMapping("/users/{id}/activate")
    @Operation(summary = "Re-activate a user")
    public AdminUserResponse activate(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id) {
        return adminService.setActive(admin.id(), id, true);
    }

    @GetMapping("/rides")
    @Operation(summary = "List rides, optionally by status")
    public PageResponse<AdminRideResponse> rides(@RequestParam(required = false) RideStatus status,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return adminService.rides(status, Paging.of(page, size, Sort.by("departureAt").descending().and(Sort.by("id"))));
    }

    @GetMapping("/reports")
    @Operation(summary = "List reports, optionally by status")
    public PageResponse<ReportResponse> reports(@RequestParam(required = false) ReportStatus status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return adminService.reports(status, Paging.of(page, size, Sort.by("createdAt").descending()));
    }

    @PatchMapping("/reports/{id}")
    @Operation(summary = "Resolve or dismiss a report")
    public ReportResponse updateReport(@PathVariable Long id, @Valid @RequestBody UpdateReportStatusRequest request) {
        return adminService.updateReport(id, request.status());
    }
}
