package com.rideshare.ride;

import java.util.EnumSet;
import java.util.Set;

public enum RideStatus {
    OPEN,
    FULL,
    STARTED,
    COMPLETED,
    CANCELLED;

    /** Statuses in which the ride still occupies its members' time. */
    public static final Set<RideStatus> ACTIVE = EnumSet.of(OPEN, FULL, STARTED);

    /** Statuses in which details, seats and membership can still change. */
    public static final Set<RideStatus> EDITABLE = EnumSet.of(OPEN, FULL);
}
