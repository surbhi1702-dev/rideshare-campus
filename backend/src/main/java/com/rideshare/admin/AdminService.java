package com.rideshare.admin;

import com.rideshare.admin.dto.AdminRideResponse;
import com.rideshare.admin.dto.AdminStatsResponse;
import com.rideshare.admin.dto.AdminUserResponse;
import com.rideshare.common.dto.PageResponse;
import com.rideshare.common.exception.ErrorCode;
import com.rideshare.common.exception.InvalidRequestException;
import com.rideshare.common.exception.ResourceNotFoundException;
import com.rideshare.ride.Ride;
import com.rideshare.ride.RideMapper;
import com.rideshare.ride.RideRepository;
import com.rideshare.ride.RideStatus;
import com.rideshare.safety.Report;
import com.rideshare.safety.ReportRepository;
import com.rideshare.safety.ReportStatus;
import com.rideshare.safety.dto.ReportResponse;
import com.rideshare.user.User;
import com.rideshare.user.UserRepository;
import com.rideshare.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final RideRepository rideRepository;
    private final ReportRepository reportRepository;
    private final RideMapper rideMapper;
    private final Clock clock;

    public AdminService(UserRepository userRepository, UserService userService, RideRepository rideRepository,
                        ReportRepository reportRepository, RideMapper rideMapper, Clock clock) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.rideRepository = rideRepository;
        this.reportRepository = reportRepository;
        this.rideMapper = rideMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> users(String query, Pageable pageable) {
        Page<User> page = query == null || query.isBlank()
                ? userRepository.findAll(pageable)
                : userRepository.search(query.trim(), pageable);
        return PageResponse.from(page, AdminService::toAdminUser);
    }

    @Transactional
    public AdminUserResponse setActive(Long adminId, Long userId, boolean active) {
        if (adminId.equals(userId) && !active) {
            throw new InvalidRequestException(ErrorCode.INVALID_REQUEST, "You cannot deactivate your own account");
        }
        User user = userService.getUser(userId);
        user.setActive(active);
        log.info("Admin {} set user {} active={}", adminId, userId, active);
        return toAdminUser(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminRideResponse> rides(RideStatus status, Pageable pageable) {
        Specification<Ride> spec = status == null
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.equal(root.get("status"), status);
        return PageResponse.from(rideRepository.findAll(spec, pageable),
                ride -> new AdminRideResponse(rideMapper.toSummary(ride), ride.getCreator().getEmail()));
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse stats() {
        Map<RideStatus, Long> byStatus = new EnumMap<>(RideStatus.class);
        for (RideStatus status : RideStatus.values()) {
            byStatus.put(status, 0L);
        }
        rideRepository.countGroupedByStatus().forEach(row -> byStatus.put(row.getStatus(), row.getTotal()));
        long totalRides = byStatus.values().stream().mapToLong(Long::longValue).sum();

        return new AdminStatsResponse(
                userRepository.count(),
                userRepository.countByActiveTrue(),
                totalRides,
                byStatus,
                rideRepository.countByDepartureAtAfterAndStatusIn(LocalDateTime.now(clock), RideStatus.EDITABLE),
                reportRepository.countByStatus(ReportStatus.OPEN),
                new BigDecimal(rideRepository.estimateSavingsOnCompletedRides().toString())
                        .setScale(2, RoundingMode.HALF_UP));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportResponse> reports(ReportStatus status, Pageable pageable) {
        Page<Report> page = status == null
                ? reportRepository.findAllBy(pageable)
                : reportRepository.findByStatus(status, pageable);
        return PageResponse.from(page, AdminService::toReportResponse);
    }

    @Transactional
    public ReportResponse updateReport(Long reportId, ReportStatus status) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REPORT_NOT_FOUND, "Report not found"));
        report.close(status, LocalDateTime.now(clock));
        return toReportResponse(report);
    }

    private static AdminUserResponse toAdminUser(User user) {
        return new AdminUserResponse(user.getId(), user.getName(), user.getEmail(), user.getPhoneNumber(),
                user.getRole(), user.isActive(), user.getCreatedAt());
    }

    private static ReportResponse toReportResponse(Report report) {
        return new ReportResponse(report.getId(),
                report.getReporter().getId(), report.getReporter().getEmail(),
                report.getReportedUser().getId(), report.getReportedUser().getEmail(),
                report.getReportedUser().isActive(),
                report.getRide() == null ? null : report.getRide().getId(),
                report.getReason(), report.getDescription(), report.getStatus(),
                report.getCreatedAt(), report.getResolvedAt());
    }
}
