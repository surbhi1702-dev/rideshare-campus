package com.rideshare.ride;

import com.rideshare.matching.geo.GeoPoint;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A shared journey. {@code occupiedSeats} is a denormalised sum of the
 * participants' booked seats; it is only ever changed while the row is locked
 * (see RideRepository#findByIdForUpdate), and a CHECK constraint keeps it within
 * {@code [0, totalSeats]} in the database.
 */
@Entity
@Table(name = "rides")
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(name = "source_name", nullable = false, length = 120)
    private String sourceName;

    @Column(name = "source_latitude", nullable = false)
    private double sourceLatitude;

    @Column(name = "source_longitude", nullable = false)
    private double sourceLongitude;

    @Column(name = "destination_name", nullable = false, length = 120)
    private String destinationName;

    @Column(name = "destination_latitude", nullable = false)
    private double destinationLatitude;

    @Column(name = "destination_longitude", nullable = false)
    private double destinationLongitude;

    @Column(name = "departure_at", nullable = false)
    private LocalDateTime departureAt;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @Column(name = "occupied_seats", nullable = false)
    private int occupiedSeats;

    @Column(name = "total_fare", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalFare;

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RideStatus status = RideStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Ride() {
        // JPA
    }

    public Ride(User creator, RideDetails details, int creatorSeats) {
        this.creator = creator;
        applyDetails(details);
        this.occupiedSeats = creatorSeats;
        refreshCapacityStatus();
    }

    /** Overwrites route, time, capacity, fare and notes. Callers validate first. */
    public void applyDetails(RideDetails details) {
        this.sourceName = details.sourceName();
        this.sourceLatitude = details.source().latitude();
        this.sourceLongitude = details.source().longitude();
        this.destinationName = details.destinationName();
        this.destinationLatitude = details.destination().latitude();
        this.destinationLongitude = details.destination().longitude();
        this.departureAt = details.departureAt();
        this.totalSeats = details.totalSeats();
        this.totalFare = details.totalFare();
        this.notes = details.notes();
    }

    public int getAvailableSeats() {
        return totalSeats - occupiedSeats;
    }

    public void occupySeats(int seats) {
        if (seats <= 0 || seats > getAvailableSeats()) {
            throw new IllegalStateException("Cannot occupy %d seats, %d available".formatted(seats, getAvailableSeats()));
        }
        occupiedSeats += seats;
        refreshCapacityStatus();
    }

    public void releaseSeats(int seats) {
        if (seats <= 0 || seats > occupiedSeats) {
            throw new IllegalStateException("Cannot release %d seats, %d occupied".formatted(seats, occupiedSeats));
        }
        occupiedSeats -= seats;
        refreshCapacityStatus();
    }

    /** OPEN <-> FULL follows capacity; other statuses are never touched here. */
    public void refreshCapacityStatus() {
        if (RideStatus.EDITABLE.contains(status)) {
            status = occupiedSeats >= totalSeats ? RideStatus.FULL : RideStatus.OPEN;
        }
    }

    public void markCancelled() {
        this.status = RideStatus.CANCELLED;
    }

    public void markStarted() {
        this.status = RideStatus.STARTED;
    }

    public void markCompleted() {
        this.status = RideStatus.COMPLETED;
    }

    public boolean isCreatedBy(Long userId) {
        return creator.getId().equals(userId);
    }

    public GeoPoint getSource() {
        return new GeoPoint(sourceLatitude, sourceLongitude);
    }

    public GeoPoint getDestination() {
        return new GeoPoint(destinationLatitude, destinationLongitude);
    }

    public Long getId() {
        return id;
    }

    public User getCreator() {
        return creator;
    }

    public String getSourceName() {
        return sourceName;
    }

    public double getSourceLatitude() {
        return sourceLatitude;
    }

    public double getSourceLongitude() {
        return sourceLongitude;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public double getDestinationLatitude() {
        return destinationLatitude;
    }

    public double getDestinationLongitude() {
        return destinationLongitude;
    }

    public LocalDateTime getDepartureAt() {
        return departureAt;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public int getOccupiedSeats() {
        return occupiedSeats;
    }

    public BigDecimal getTotalFare() {
        return totalFare;
    }

    public String getNotes() {
        return notes;
    }

    public RideStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
