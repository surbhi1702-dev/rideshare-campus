package com.rideshare.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByActiveTrue();

    @Query("""
            select u from User u
            where lower(u.name) like lower(concat('%', :query, '%'))
               or lower(u.email) like lower(concat('%', :query, '%'))
            """)
    Page<User> search(@Param("query") String query, Pageable pageable);
}
