package com.rideshare.ride.waitlist;

/**
 * What the ride detail view shows about the queue.
 *
 * @param size           students currently waiting
 * @param viewerPosition the viewer's 1-based place in the queue, or null if not waiting
 * @param viewerSeats    seats the viewer asked for, or null if not waiting
 */
public record WaitlistStatus(int size, Integer viewerPosition, Integer viewerSeats) {

    public static final WaitlistStatus EMPTY = new WaitlistStatus(0, null, null);

    public boolean viewerIsWaiting() {
        return viewerPosition != null;
    }
}
