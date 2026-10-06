package com.rideshare.ride.waitlist;

import com.rideshare.ride.Ride;
import com.rideshare.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/** A student queued for a full ride. Arrival order is (createdAt, id). */
@Entity
@Table(name = "ride_waitlist",
        uniqueConstraints = @UniqueConstraint(name = "uk_waitlist_ride_user", columnNames = {"ride_id", "user_id"}))
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "seats_requested", nullable = false)
    private int seatsRequested;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected WaitlistEntry() {
        // JPA
    }

    public WaitlistEntry(Ride ride, User user, int seatsRequested, LocalDateTime createdAt) {
        this.ride = ride;
        this.user = user;
        this.seatsRequested = seatsRequested;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Ride getRide() {
        return ride;
    }

    public User getUser() {
        return user;
    }

    public int getSeatsRequested() {
        return seatsRequested;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
