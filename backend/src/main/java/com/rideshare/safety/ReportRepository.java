package com.rideshare.safety;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @EntityGraph(attributePaths = {"reporter", "reportedUser", "ride"})
    Page<Report> findByStatus(ReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "reportedUser", "ride"})
    Page<Report> findAllBy(Pageable pageable);

    long countByStatus(ReportStatus status);
}
