package com.rideshare.ride.waitlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    /** One ride's queue in arrival order (uses idx_waitlist_ride_created). */
    @Query("select w from WaitlistEntry w join fetch w.user where w.ride.id = :rideId order by w.createdAt asc, w.id asc")
    List<WaitlistEntry> findQueue(@Param("rideId") Long rideId);

    Optional<WaitlistEntry> findByRideIdAndUserId(Long rideId, Long userId);

    boolean existsByRideIdAndUserId(Long rideId, Long userId);

    long countByRideId(Long rideId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from WaitlistEntry w where w.ride.id = :rideId and w.user.id = :userId")
    int deleteByRideIdAndUserId(@Param("rideId") Long rideId, @Param("userId") Long userId);
}
