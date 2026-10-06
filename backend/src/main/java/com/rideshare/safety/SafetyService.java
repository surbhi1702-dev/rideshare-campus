package com.rideshare.safety;

import com.rideshare.common.exception.ConflictException;
import com.rideshare.common.exception.ErrorCode;
import com.rideshare.common.exception.ForbiddenOperationException;
import com.rideshare.common.exception.InvalidRequestException;
import com.rideshare.common.exception.ResourceNotFoundException;
import com.rideshare.ride.Ride;
import com.rideshare.ride.RideParticipantRepository;
import com.rideshare.ride.RideRepository;
import com.rideshare.safety.dto.BlockedUserResponse;
import com.rideshare.safety.dto.CreateReportRequest;
import com.rideshare.safety.dto.ReportSubmittedResponse;
import com.rideshare.user.User;
import com.rideshare.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Student-facing safety tools: block and report. Moderation is in AdminService. */
@Service
public class SafetyService {

    private static final Logger log = LoggerFactory.getLogger(SafetyService.class);

    private final UserBlockRepository userBlockRepository;
    private final ReportRepository reportRepository;
    private final RideRepository rideRepository;
    private final RideParticipantRepository participantRepository;
    private final UserService userService;

    public SafetyService(UserBlockRepository userBlockRepository, ReportRepository reportRepository,
                         RideRepository rideRepository, RideParticipantRepository participantRepository,
                         UserService userService) {
        this.userBlockRepository = userBlockRepository;
        this.reportRepository = reportRepository;
        this.rideRepository = rideRepository;
        this.participantRepository = participantRepository;
        this.userService = userService;
    }

    @Transactional
    public BlockedUserResponse block(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new InvalidRequestException(ErrorCode.INVALID_REQUEST, "You cannot block yourself");
        }
        User blocker = userService.getActiveUser(blockerId);
        User blocked = userService.getUser(blockedId);
        if (userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            throw new ConflictException(ErrorCode.ALREADY_BLOCKED, "You have already blocked this user");
        }
        UserBlock block = userBlockRepository.save(new UserBlock(blocker, blocked));
        userBlockRepository.flush();
        return new BlockedUserResponse(blocked.getId(), blocked.getDisplayName(), block.getCreatedAt());
    }

    @Transactional
    public void unblock(Long blockerId, Long blockedId) {
        UserBlock block = userBlockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .orElseThrow(() -> new ResourceNotFoundException("You have not blocked this user"));
        userBlockRepository.delete(block);
    }

    @Transactional(readOnly = true)
    public List<BlockedUserResponse> listBlocked(Long blockerId) {
        return userBlockRepository.findAllByBlocker(blockerId).stream()
                .map(b -> new BlockedUserResponse(b.getBlocked().getId(), b.getBlocked().getDisplayName(),
                        b.getCreatedAt()))
                .toList();
    }

    /**
     * Students can only report someone they actually shared a ride with (or whose
     * ride they are reporting). This keeps the report channel from being used to
     * harass arbitrary users.
     */
    @Transactional
    public ReportSubmittedResponse report(Long reporterId, CreateReportRequest request) {
        if (reporterId.equals(request.reportedUserId())) {
            throw new InvalidRequestException(ErrorCode.INVALID_REQUEST, "You cannot report yourself");
        }
        User reporter = userService.getActiveUser(reporterId);
        User reported = userService.getUser(request.reportedUserId());

        Ride ride = null;
        if (request.rideId() != null) {
            ride = rideRepository.findById(request.rideId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RIDE_NOT_FOUND, "Ride not found"));
            boolean reportedInRide = participantRepository.existsByRideIdAndUserId(ride.getId(), reported.getId());
            if (!reportedInRide) {
                throw new InvalidRequestException(ErrorCode.INVALID_REQUEST, "That user is not part of this ride");
            }
        } else if (!participantRepository.haveSharedRide(reporterId, reported.getId())) {
            throw new ForbiddenOperationException("You can only report students you have shared a ride with");
        }

        String description = request.description() == null || request.description().isBlank()
                ? null : request.description().trim();
        Report report = reportRepository.save(new Report(reporter, reported, ride, request.reason(), description));
        log.info("Report {} filed against user {} ({})", report.getId(), reported.getId(), request.reason());
        return new ReportSubmittedResponse(report.getId(), report.getStatus());
    }
}
