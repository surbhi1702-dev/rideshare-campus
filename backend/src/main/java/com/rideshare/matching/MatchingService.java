package com.rideshare.matching;

import com.rideshare.common.exception.ErrorCode;
import com.rideshare.common.exception.ForbiddenOperationException;
import com.rideshare.common.exception.InvalidRequestException;
import com.rideshare.common.exception.ResourceNotFoundException;
import com.rideshare.matching.dto.MatchSearchRequest;
import com.rideshare.matching.dto.RideMatchResponse;
import com.rideshare.matching.geo.GeoPoint;
import com.rideshare.ride.FareCalculator;
import com.rideshare.ride.Ride;
import com.rideshare.ride.RideMapper;
import com.rideshare.ride.RideParticipant;
import com.rideshare.ride.RideParticipantRepository;
import com.rideshare.ride.RideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** API-facing wrapper around the {@link MatchingEngine}: access checks and DTO mapping. */
@Service
public class MatchingService {

    private final MatchingEngine matchingEngine;
    private final RideRepository rideRepository;
    private final RideParticipantRepository participantRepository;
    private final RideMapper rideMapper;
    private final FareCalculator fareCalculator;

    public MatchingService(MatchingEngine matchingEngine, RideRepository rideRepository,
                           RideParticipantRepository participantRepository, RideMapper rideMapper,
                           FareCalculator fareCalculator) {
        this.matchingEngine = matchingEngine;
        this.rideRepository = rideRepository;
        this.participantRepository = participantRepository;
        this.rideMapper = rideMapper;
        this.fareCalculator = fareCalculator;
    }

    /**
     * Other rides that fit a ride the requester is part of (typically their own),
     * e.g. to merge two half-empty cabs.
     */
    @Transactional(readOnly = true)
    public List<RideMatchResponse> matchesForRide(Long rideId, Long requesterId) {
        Ride ride = rideRepository.findWithCreatorById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RIDE_NOT_FOUND, "Ride not found"));
        RideParticipant participation = participantRepository.findByRideIdAndUserId(rideId, requesterId)
                .orElseThrow(() -> new ForbiddenOperationException("You can only see matches for rides you are part of"));

        MatchQuery query = new MatchQuery(requesterId, ride.getSource(), ride.getDestination(), ride.getDepartureAt(),
                participation.getSeatsBooked(), rideId);
        return toResponses(matchingEngine.findMatches(query), participation.getSeatsBooked());
    }

    @Transactional(readOnly = true)
    public List<RideMatchResponse> search(MatchSearchRequest request, Long requesterId) {
        int seats = request.seats() == null ? 1 : request.seats();
        GeoPoint source = new GeoPoint(request.sourceLatitude(), request.sourceLongitude());
        GeoPoint destination = new GeoPoint(request.destinationLatitude(), request.destinationLongitude());
        if (source.equals(destination)) {
            throw new InvalidRequestException(ErrorCode.INVALID_ROUTE, "Source and destination must differ");
        }
        LocalDateTime departureAt = LocalDateTime.of(request.date(), request.time());
        MatchQuery query = new MatchQuery(requesterId, source, destination, departureAt, seats, null);
        return toResponses(matchingEngine.findMatches(query), seats);
    }

    private List<RideMatchResponse> toResponses(List<MatchResult> matches, int seats) {
        return matches.stream()
                .map(match -> new RideMatchResponse(rideMapper.toSummary(match.ride()), match.score(),
                        fareCalculator.shareIfJoined(match.ride().getTotalFare(), match.ride().getOccupiedSeats(), seats)))
                .toList();
    }
}
