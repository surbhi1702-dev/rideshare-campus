package com.rideshare.ride;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RideParticipantRepository extends JpaRepository<RideParticipant, Long> {

    Optional<RideParticipant> findByRideIdAndUserId(Long rideId, Long userId);

    boolean existsByRideIdAndUserId(Long rideId, Long userId);

    long countByRideId(Long rideId);

    @Query("select p from RideParticipant p join fetch p.user where p.ride.id = :rideId order by p.joinedAt asc, p.id asc")
    List<RideParticipant> findAllWithUserByRideId(@Param("rideId") Long rideId);

    @Query("select p.user.id from RideParticipant p where p.ride.id = :rideId")
    List<Long> findUserIdsByRideId(@Param("rideId") Long rideId);

    /** Used to stop a student from being in two groups that leave at (almost) the same time. */
    @Query("""
            select count(p) > 0 from RideParticipant p join p.ride r
            where p.user.id = :userId
              and r.status in :activeStatuses
              and r.departureAt between :windowStart and :windowEnd
              and r.id not in :excludedRideIds
            """)
    boolean existsActiveRideInWindow(@Param("userId") Long userId,
                                     @Param("activeStatuses") Collection<RideStatus> activeStatuses,
                                     @Param("windowStart") LocalDateTime windowStart,
                                     @Param("windowEnd") LocalDateTime windowEnd,
                                     @Param("excludedRideIds") Collection<Long> excludedRideIds);

    @Query("""
            select count(p) > 0 from RideParticipant p1, RideParticipant p
            where p1.user.id = :userA and p.user.id = :userB and p1.ride.id = p.ride.id
            """)
    boolean haveSharedRide(@Param("userA") Long userA, @Param("userB") Long userB);
}
