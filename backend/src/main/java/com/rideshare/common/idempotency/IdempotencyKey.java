package com.rideshare.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** "User U already performed operation O on ride R with key K." */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idem_key", nullable = false, length = 100)
    private String key;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IdempotentOperation operation;

    @Column(name = "ride_id", nullable = false)
    private Long rideId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected IdempotencyKey() {
        // JPA
    }

    public IdempotencyKey(Long userId, String key, IdempotentOperation operation, Long rideId, LocalDateTime createdAt) {
        this.userId = userId;
        this.key = key;
        this.operation = operation;
        this.rideId = rideId;
        this.createdAt = createdAt;
    }

    public boolean matches(IdempotentOperation operation, Long rideId) {
        return this.operation == operation && this.rideId.equals(rideId);
    }
}
