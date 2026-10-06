package com.rideshare.safety;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    Optional<UserBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    @Query("select b from UserBlock b join fetch b.blocked where b.blocker.id = :userId order by b.createdAt desc")
    List<UserBlock> findAllByBlocker(@Param("userId") Long userId);

    @Query("select b.blocked.id from UserBlock b where b.blocker.id = :userId")
    List<Long> findIdsBlockedBy(@Param("userId") Long userId);

    @Query("select b.blocker.id from UserBlock b where b.blocked.id = :userId")
    List<Long> findIdsWhoBlocked(@Param("userId") Long userId);
}
