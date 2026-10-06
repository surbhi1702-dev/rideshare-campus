package com.rideshare.safety;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Answers "who must never be grouped with this student?" - everyone they
 * blocked plus everyone who blocked them.
 */
@Component
public class BlockLookup {

    /** JPQL "in ()" with an empty list is invalid SQL; -1 is never a real id. */
    private static final Long NO_USER_SENTINEL = -1L;

    private final UserBlockRepository userBlockRepository;

    public BlockLookup(UserBlockRepository userBlockRepository) {
        this.userBlockRepository = userBlockRepository;
    }

    @Transactional(readOnly = true)
    public Set<Long> blockedRelations(Long userId) {
        Set<Long> ids = new HashSet<>(userBlockRepository.findIdsBlockedBy(userId));
        ids.addAll(userBlockRepository.findIdsWhoBlocked(userId));
        return ids;
    }

    /** Same as {@link #blockedRelations(Long)} but safe to bind to a JPQL IN clause. */
    @Transactional(readOnly = true)
    public List<Long> blockedRelationsForQuery(Long userId) {
        Set<Long> ids = blockedRelations(userId);
        return ids.isEmpty() ? List.of(NO_USER_SENTINEL) : List.copyOf(ids);
    }
}
