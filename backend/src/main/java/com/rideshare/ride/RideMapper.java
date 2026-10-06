package com.rideshare.ride;

import com.rideshare.ride.dto.FareSplitResponse;
import com.rideshare.ride.dto.ParticipantResponse;
import com.rideshare.ride.dto.PublicUserSummary;
import com.rideshare.ride.dto.RideActions;
import com.rideshare.ride.dto.RideDetailResponse;
import com.rideshare.ride.dto.RideSummaryResponse;
import com.rideshare.ride.waitlist.WaitlistStatus;
import com.rideshare.user.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class RideMapper {

    private final FareCalculator fareCalculator;

    public RideMapper(FareCalculator fareCalculator) {
        this.fareCalculator = fareCalculator;
    }

    public RideSummaryResponse toSummary(Ride ride) {
        return new RideSummaryResponse(
                ride.getId(),
                ride.getSourceName(), ride.getSourceLatitude(), ride.getSourceLongitude(),
                ride.getDestinationName(), ride.getDestinationLatitude(), ride.getDestinationLongitude(),
                ride.getDepartureAt(),
                ride.getTotalSeats(), ride.getOccupiedSeats(), ride.getAvailableSeats(),
                ride.getTotalFare(),
                fareCalculator.sharePerSeat(ride.getTotalFare(), Math.max(ride.getOccupiedSeats(), 1)),
                ride.getStatus(),
                toPublic(ride.getCreator()));
    }

    public PublicUserSummary toPublic(User user) {
        return new PublicUserSummary(user.getId(), user.getDisplayName());
    }

    /**
     * @param participants all participants in join order
     * @param viewer       the viewer's participation, or null if not a member
     */
    public RideDetailResponse toDetail(Ride ride, List<RideParticipant> participants, RideParticipant viewer,
                                       WaitlistStatus waitlist, RideActions actions) {
        boolean member = viewer != null;
        List<ParticipantResponse> visibleParticipants = List.of();
        FareSplitResponse fareSplit = null;
        BigDecimal shareIfJoined = null;

        if (member) {
            List<BigDecimal> shares = fareCalculator.splitExact(ride.getTotalFare(),
                    participants.stream().map(RideParticipant::getSeatsBooked).toList());
            List<ParticipantResponse> mapped = new ArrayList<>(participants.size());
            BigDecimal yourShare = BigDecimal.ZERO;
            for (int i = 0; i < participants.size(); i++) {
                RideParticipant participant = participants.get(i);
                User user = participant.getUser();
                mapped.add(new ParticipantResponse(user.getId(), user.getName(), user.getPhoneNumber(),
                        participant.getSeatsBooked(), participant.getRole(), participant.getJoinedAt(), shares.get(i)));
                if (participant.getId().equals(viewer.getId())) {
                    yourShare = shares.get(i);
                }
            }
            visibleParticipants = mapped;
            fareSplit = new FareSplitResponse(ride.getTotalFare(), ride.getOccupiedSeats(),
                    fareCalculator.sharePerSeat(ride.getTotalFare(), ride.getOccupiedSeats()), yourShare);
        } else if (ride.getAvailableSeats() > 0) {
            shareIfJoined = fareCalculator.shareIfJoined(ride.getTotalFare(), ride.getOccupiedSeats(), 1);
        }

        return new RideDetailResponse(toSummary(ride), ride.getNotes(), member ? viewer.getRole() : null,
                participants.size(), visibleParticipants, fareSplit, shareIfJoined, waitlist, actions);
    }
}
