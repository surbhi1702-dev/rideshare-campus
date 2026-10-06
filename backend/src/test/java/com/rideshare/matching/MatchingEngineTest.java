package com.rideshare.matching;

import com.rideshare.matching.geo.HaversineDistanceCalculator;
import com.rideshare.ride.Ride;
import com.rideshare.ride.RideRepository;
import com.rideshare.safety.BlockLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static com.rideshare.support.TestRides.AIRPORT;
import static com.rideshare.support.TestRides.CAMPUS;
import static com.rideshare.support.TestRides.CUTTACK;
import static com.rideshare.support.TestRides.RAILWAY_STATION;
import static com.rideshare.support.TestRides.ride;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingEngineTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 10, 7, 0);
    private static final LocalDateTime NINE_AM = NOW.plusHours(2);

    @Mock
    private RideRepository rideRepository;
    @Mock
    private BlockLookup blockLookup;

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        MatchingProperties props = new MatchingProperties(3.0, 5.0, 60, 3, 10,
                new MatchingProperties.Weights(0.30, 0.35, 0.25, 0.10));
        Clock clock = Clock.fixed(ZonedDateTime.of(NOW, ZONE).toInstant(), ZONE);
        engine = new MatchingEngine(rideRepository, new MatchScorer(new HaversineDistanceCalculator(), props), props,
                blockLookup, clock);
    }

    private void candidatesAre(List<Ride> rides) {
        when(blockLookup.blockedRelationsForQuery(1L)).thenReturn(List.of(-1L));
        when(rideRepository.findMatchCandidates(any(), any(), any(), anyInt(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyLong(), eq(1L), any()))
                .thenReturn(rides);
    }

    @Test
    void ranksCompatibleRidesBestFirstAndDropsIncompatibleOnes() {
        Ride perfect = ride(10, CAMPUS, AIRPORT, NINE_AM, 4, 1);
        Ride later = ride(11, CAMPUS, AIRPORT, NINE_AM.plusMinutes(40), 4, 1);
        Ride nearbyDrop = ride(12, CAMPUS, RAILWAY_STATION, NINE_AM, 4, 1);
        Ride wrongCity = ride(13, CAMPUS, CUTTACK, NINE_AM, 4, 1); // passes SQL box in this mock, fails Haversine
        candidatesAre(List.of(wrongCity, later, nearbyDrop, perfect));

        List<MatchResult> results = engine.findMatches(new MatchQuery(1L, CAMPUS, AIRPORT, NINE_AM, 1, null));

        assertThat(results).extracting(r -> r.ride().getId()).containsExactly(10L, 11L, 12L);
    }

    @Test
    void returnsAtMostMaxResults() {
        candidatesAre(List.of(
                ride(1, CAMPUS, AIRPORT, NINE_AM, 4, 1),
                ride(2, CAMPUS, AIRPORT, NINE_AM.plusMinutes(5), 4, 1),
                ride(3, CAMPUS, AIRPORT, NINE_AM.plusMinutes(10), 4, 1),
                ride(4, CAMPUS, AIRPORT, NINE_AM.plusMinutes(15), 4, 1)));

        assertThat(engine.findMatches(new MatchQuery(1L, CAMPUS, AIRPORT, NINE_AM, 1, null))).hasSize(3);
    }

    @Test
    void equalScoresAreOrderedDeterministicallyById() {
        candidatesAre(List.of(ride(7, CAMPUS, AIRPORT, NINE_AM, 4, 1), ride(3, CAMPUS, AIRPORT, NINE_AM, 4, 1)));

        List<MatchResult> results = engine.findMatches(new MatchQuery(1L, CAMPUS, AIRPORT, NINE_AM, 1, null));

        assertThat(results).extracting(r -> r.ride().getId()).containsExactly(3L, 7L);
    }

    @Test
    void passesTimeWindowAndExcludedRideToTheQuery() {
        candidatesAre(List.of());
        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);

        engine.findMatches(new MatchQuery(1L, CAMPUS, AIRPORT, NINE_AM, 1, 42L));

        verify(rideRepository).findMatchCandidates(eq(NOW), start.capture(), end.capture(), eq(1), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq(42L),
                eq(1L), any());
        assertThat(start.getValue()).isEqualTo(NINE_AM.minusMinutes(60));
        assertThat(end.getValue()).isEqualTo(NINE_AM.plusMinutes(60));
    }

    @Test
    void journeyEntirelyInThePastSkipsTheDatabase() {
        engine.findMatches(new MatchQuery(1L, CAMPUS, AIRPORT, NOW.minusHours(3), 1, null));

        verifyNoInteractions(blockLookup);
        verify(rideRepository, never()).findMatchCandidates(any(), any(), any(), anyInt(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyLong(), any(), any());
    }
}
