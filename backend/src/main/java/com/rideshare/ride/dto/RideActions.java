package com.rideshare.ride.dto;

/** What the current viewer may do now - lets the UI show only valid buttons. */
public record RideActions(
        boolean canJoin,
        boolean canLeave,
        boolean canEdit,
        boolean canCancel,
        boolean canStart,
        boolean canComplete,
        boolean canJoinWaitlist,
        boolean canLeaveWaitlist
) {
}
