package com.rideshare.matching;

import com.rideshare.matching.geo.BoundingBox;
import com.rideshare.ride.Ride;
import com.rideshare.ride.RideRepository;
import com.rideshare.safety.BlockLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Finds and ranks rides a student could join.
 *
 * <ol>
 *   <li><b>Candidate retrieval</b> (SQL, indexed): OPEN rides departing inside the
 *       time window, with enough free seats, whose pickup and drop points fall in
 *       bounding boxes around the query points; excludes the requester's own rides
 *       and any ride containing a blocked user.</li>
 *   <li><b>Elimination</b>: exact Haversine distances and time difference against
 *       the configured maxima (bounding boxes are larger than the circles).</li>
 *   <li><b>Scoring</b>: normalised weighted cost, see {@link MatchScorer}.</li>
 *   <li><b>Ranking</b>: by score, ties broken by smaller time difference, then by ride id.</li>
 *   <li><b>Top-K</b>: first {@code max-results} matches.</li>
 * </ol>
 *
 * Complexity: with C candidates returned by step 1, steps 2-3 are O(C) and the sort
 * O(C log C). C is small because the index restricts rows to one time window.
 */
@Service
public class MatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);
    private static final long NO_RIDE = -1L;

    private static final Comparator<MatchResult> RANKING = Comparator
            .comparingDouble((MatchResult m) -> m.score().score())
            .thenComparingLong(m -> m.score().timeDifferenceMinutes())
            .thenComparingLong(m -> m.ride().getId());

    private final RideRepository rideRepository;
    private final MatchScorer matchScorer;
    private final MatchingProperties properties;
    private final BlockLookup blockLookup;
    private final Clock clock;

    public MatchingEngine(RideRepository rideRepository, MatchScorer matchScorer, MatchingProperties properties,
                          BlockLookup blockLookup, Clock clock) {
        this.rideRepository = rideRepository;
        this.matchScorer = matchScorer;
        this.properties = properties;
        this.blockLookup = blockLookup;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MatchResult> findMatches(MatchQuery query) {
        LocalDateTime now = LocalDateTime.now(clock);
        Duration tolerance = Duration.ofMinutes(properties.maxTimeDifferenceMinutes());
        LocalDateTime windowStart = query.departureAt().minus(tolerance);
        LocalDateTime windowEnd = query.departureAt().plus(tolerance);
        if (windowEnd.isBefore(now)) {
            return List.of();
        }

        BoundingBox sourceBox = BoundingBox.around(query.source(), properties.maxPickupDistanceKm());
        BoundingBox destinationBox = BoundingBox.around(query.destination(), properties.maxDestinationDistanceKm());

        List<Ride> candidates = rideRepository.findMatchCandidates(now, windowStart, windowEnd, query.seatsNeeded(),
                sourceBox.minLatitude(), sourceBox.maxLatitude(), sourceBox.minLongitude(), sourceBox.maxLongitude(),
                destinationBox.minLatitude(), destinationBox.maxLatitude(),
                destinationBox.minLongitude(), destinationBox.maxLongitude(),
                Optional.ofNullable(query.excludedRideId()).orElse(NO_RIDE),
                query.requesterId(),
                blockLookup.blockedRelationsForQuery(query.requesterId()));

        List<MatchResult> ranked = candidates.stream()
                .flatMap(ride -> matchScorer.score(query, ride).map(score -> new MatchResult(ride, score)).stream())
                .sorted(RANKING)
                .limit(properties.maxResults())
                .toList();

        log.debug("Matching for user {}: {} candidates, {} compatible", query.requesterId(), candidates.size(),
                ranked.size());
        return ranked;
    }
}
