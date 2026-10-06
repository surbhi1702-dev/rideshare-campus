package com.rideshare.ride;

import com.rideshare.ride.dto.RideBrowseFilter;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Dynamic filters for browsing open rides. */
public final class RideSpecifications {

    private RideSpecifications() {
    }

    public static Specification<Ride> browse(RideBrowseFilter filter, LocalDateTime now, Collection<Long> blockedUserIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), RideStatus.OPEN));
            predicates.add(cb.greaterThan(root.<LocalDateTime>get("departureAt"), now));

            if (hasText(filter.source())) {
                predicates.add(cb.like(cb.lower(root.get("sourceName")), containsPattern(filter.source()), '\\'));
            }
            if (hasText(filter.destination())) {
                predicates.add(cb.like(cb.lower(root.get("destinationName")), containsPattern(filter.destination()), '\\'));
            }
            if (filter.date() != null) {
                LocalTime from = filter.fromTime() != null ? filter.fromTime() : LocalTime.MIN;
                LocalDateTime start = filter.date().atTime(from);
                LocalDateTime end = filter.toTime() != null
                        ? filter.date().atTime(filter.toTime())
                        : filter.date().plusDays(1).atStartOfDay().minusNanos(1);
                predicates.add(cb.between(root.get("departureAt"), start, end));
            }
            if (filter.minSeats() != null && filter.minSeats() > 0) {
                predicates.add(cb.greaterThanOrEqualTo(
                        cb.diff(root.<Integer>get("totalSeats"), root.<Integer>get("occupiedSeats")), filter.minSeats()));
            }

            // Hide rides that contain someone the viewer blocked (or who blocked the viewer).
            Subquery<Long> blockedMember = query.subquery(Long.class);
            Root<RideParticipant> participant = blockedMember.from(RideParticipant.class);
            blockedMember.select(participant.get("id")).where(
                    cb.equal(participant.get("ride"), root),
                    participant.get("user").get("id").in(blockedUserIds));
            predicates.add(cb.not(cb.exists(blockedMember)));

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String containsPattern(String raw) {
        String escaped = raw.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
