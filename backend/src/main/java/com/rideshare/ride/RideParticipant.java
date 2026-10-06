package com.rideshare.ride;

import com.rideshare.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/** Membership of a student in a ride (the creator is a participant too). */
@Entity
@Table(name = "ride_participants",
        uniqueConstraints = @UniqueConstraint(name = "uk_participant_ride_user", columnNames = {"ride_id", "user_id"}))
public class RideParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "seats_booked", nullable = false)
    private int seatsBooked;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParticipantRole role;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    protected RideParticipant() {
        // JPA
    }

    public RideParticipant(Ride ride, User user, int seatsBooked, ParticipantRole role, LocalDateTime joinedAt) {
        this.ride = ride;
        this.user = user;
        this.seatsBooked = seatsBooked;
        this.role = role;
        this.joinedAt = joinedAt;
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

    public int getSeatsBooked() {
        return seatsBooked;
    }

    public ParticipantRole getRole() {
        return role;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
}
