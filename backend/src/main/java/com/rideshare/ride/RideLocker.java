package com.rideshare.ride;

import com.rideshare.common.exception.ErrorCode;
import com.rideshare.common.exception.ResourceNotFoundException;
import com.rideshare.config.AppProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acquires a row-level write lock ({@code SELECT ... FOR UPDATE}) on a ride.
 *
 * <p>Every operation that changes seats, status or membership of a ride first
 * locks the ride row, so those operations are serialised per ride while
 * different rides proceed fully in parallel. A bounded {@code lock_timeout}
 * turns a pathological wait into a clean 409 instead of a hung request.</p>
 *
 * <p>Annotated with {@link Repository} so lock failures are translated into
 * Spring's {@code PessimisticLockingFailureException} hierarchy.</p>
 */
@Repository
public class RideLocker {

    @PersistenceContext
    private EntityManager entityManager;

    private final String lockTimeout;

    public RideLocker(AppProperties properties) {
        this.lockTimeout = properties.ride().lockTimeoutMs() + "ms";
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Ride lock(Long rideId) {
        // SET LOCAL: applies only to the current transaction.
        entityManager.createNativeQuery("select set_config('lock_timeout', :timeout, true)")
                .setParameter("timeout", lockTimeout)
                .getSingleResult();
        Ride ride = entityManager.find(Ride.class, rideId, LockModeType.PESSIMISTIC_WRITE);
        if (ride == null) {
            throw new ResourceNotFoundException(ErrorCode.RIDE_NOT_FOUND, "Ride not found");
        }
        return ride;
    }
}
