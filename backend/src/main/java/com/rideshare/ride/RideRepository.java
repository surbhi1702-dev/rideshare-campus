package com.rideshare.ride;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RideRepository extends JpaRepository<Ride, Long>, JpaSpecificationExecutor<Ride> {

    @Query("select r from Ride r join fetch r.creator where r.id = :id")
    Optional<Ride> findWithCreatorById(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = "creator")
    Page<Ride> findAll(Specification<Ride> specification, Pageable pageable);

    /**
     * Stage 1 of matching: cheap, index-assisted candidate retrieval.
     * <ul>
     *   <li>(status, departure_at) index narrows to OPEN rides in the time window,</li>
     *   <li>bounding boxes discard rides whose pickup/drop is obviously too far,</li>
     *   <li>seat availability, own rides and blocked users are excluded in SQL.</li>
     * </ul>
     * Exact Haversine distances and scoring happen afterwards in the MatchingEngine.
     * {@code excludedUserIds} and must never be empty (callers pass a -1 sentinel).
     */
    @Query("""
            select r from Ride r join fetch r.creator
            where r.status = com.rideshare.ride.RideStatus.OPEN
              and r.departureAt between :windowStart and :windowEnd
              and r.departureAt > :now
              and (r.totalSeats - r.occupiedSeats) >= :seatsNeeded
              and r.sourceLatitude between :srcMinLat and :srcMaxLat
              and r.sourceLongitude between :srcMinLng and :srcMaxLng
              and r.destinationLatitude between :dstMinLat and :dstMaxLat
              and r.destinationLongitude between :dstMinLng and :dstMaxLng
              and r.id <> :excludedRideId
              and not exists (
                    select 1 from RideParticipant p
                    where p.ride = r and (p.user.id = :requesterId or p.user.id in :excludedUserIds))
            """)
    List<Ride> findMatchCandidates(@Param("now") LocalDateTime now,
                                   @Param("windowStart") LocalDateTime windowStart,
                                   @Param("windowEnd") LocalDateTime windowEnd,
                                   @Param("seatsNeeded") int seatsNeeded,
                                   @Param("srcMinLat") double srcMinLat,
                                   @Param("srcMaxLat") double srcMaxLat,
                                   @Param("srcMinLng") double srcMinLng,
                                   @Param("srcMaxLng") double srcMaxLng,
                                   @Param("dstMinLat") double dstMinLat,
                                   @Param("dstMaxLat") double dstMaxLat,
                                   @Param("dstMinLng") double dstMinLng,
                                   @Param("dstMaxLng") double dstMaxLng,
                                   @Param("excludedRideId") long excludedRideId,
                                   @Param("requesterId") Long requesterId,
                                   @Param("excludedUserIds") Collection<Long> excludedUserIds);

    @Query(value = """
            select r from Ride r join fetch r.creator
            where exists (select 1 from RideParticipant p where p.ride = r and p.user.id = :userId)
              and r.status in :activeStatuses
              and (r.departureAt >= :now or r.status = :started)
            """,
            countQuery = """
            select count(r) from Ride r
            where exists (select 1 from RideParticipant p where p.ride = r and p.user.id = :userId)
              and r.status in :activeStatuses
              and (r.departureAt >= :now or r.status = :started)
            """)
    Page<Ride> findUpcomingForUser(@Param("userId") Long userId,
                                   @Param("now") LocalDateTime now,
                                   @Param("activeStatuses") Collection<RideStatus> activeStatuses,
                                   @Param("started") RideStatus started,
                                   Pageable pageable);

    @Query(value = """
            select r from Ride r join fetch r.creator
            where exists (select 1 from RideParticipant p where p.ride = r and p.user.id = :userId)
              and (r.status not in :activeStatuses or (r.departureAt < :now and r.status <> :started))
            """,
            countQuery = """
            select count(r) from Ride r
            where exists (select 1 from RideParticipant p where p.ride = r and p.user.id = :userId)
              and (r.status not in :activeStatuses or (r.departureAt < :now and r.status <> :started))
            """)
    Page<Ride> findPastForUser(@Param("userId") Long userId,
                               @Param("now") LocalDateTime now,
                               @Param("activeStatuses") Collection<RideStatus> activeStatuses,
                               @Param("started") RideStatus started,
                               Pageable pageable);

    @Query("select r.status as status, count(r) as total from Ride r group by r.status")
    List<StatusCount> countGroupedByStatus();

    /**
     * Rough money saved on completed rides: n riders sharing one fare instead of
     * each paying it alone saves (n - 1) x fare. An estimate, not accounting data.
     */
    @Query("""
            select coalesce(sum(r.totalFare * (r.occupiedSeats - 1)), 0) from Ride r
            where r.status = com.rideshare.ride.RideStatus.COMPLETED and r.occupiedSeats > 1
            """)
    Number estimateSavingsOnCompletedRides();

    long countByDepartureAtAfterAndStatusIn(LocalDateTime after, Collection<RideStatus> statuses);

    interface StatusCount {
        RideStatus getStatus();

        long getTotal();
    }
}
