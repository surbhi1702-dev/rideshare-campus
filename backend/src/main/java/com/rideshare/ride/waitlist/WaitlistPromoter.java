package com.rideshare.ride.waitlist;

import com.rideshare.notification.NotificationService;
import com.rideshare.notification.NotificationType;
import com.rideshare.notification.RealtimePublisher;
import com.rideshare.notification.dto.RideEvent;
import com.rideshare.ride.OverlapGuard;
import com.rideshare.ride.ParticipantRole;
import com.rideshare.ride.Ride;
import com.rideshare.ride.RideMessages;
import com.rideshare.ride.RideParticipant;
import com.rideshare.ride.RideParticipantRepository;
import com.rideshare.ride.RideStatus;
import com.rideshare.safety.BlockLookup;
import com.rideshare.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Hands freed seats to the waitlist.
 *
 * <p>Called by whoever just freed seats (a member leaving, the creator raising the
 * seat count), <b>inside the same transaction and while the ride row is still
 * locked</b>. So between "seat freed" and "seat given to the next waiter" no other
 * request can grab it: a direct join on the same ride waits on the row lock and then
 * sees the seat already taken.</p>
 *
 * <p>Order is first-fit in arrival order: the queue is walked oldest first and each
 * waiter whose requested seats fit is promoted. A waiter asking for 2 seats is
 * skipped (keeps their place) when only 1 is free, so 1-seat requests behind them
 * are not blocked. Eligibility is re-checked at promotion time, because the
 * waiter may have joined another ride at the same time, been blocked by a member,
 * or been deactivated since they queued; such waiters are removed and told why.</p>
 */
@Component
public class WaitlistPromoter {

    private static final Logger log = LoggerFactory.getLogger(WaitlistPromoter.class);

    private final WaitlistEntryRepository waitlistRepository;
    private final RideParticipantRepository participantRepository;
    private final OverlapGuard overlapGuard;
    private final BlockLookup blockLookup;
    private final NotificationService notificationService;
    private final RealtimePublisher realtimePublisher;

    public WaitlistPromoter(WaitlistEntryRepository waitlistRepository, RideParticipantRepository participantRepository,
                            OverlapGuard overlapGuard, BlockLookup blockLookup, NotificationService notificationService,
                            RealtimePublisher realtimePublisher) {
        this.waitlistRepository = waitlistRepository;
        this.participantRepository = participantRepository;
        this.overlapGuard = overlapGuard;
        this.blockLookup = blockLookup;
        this.notificationService = notificationService;
        this.realtimePublisher = realtimePublisher;
    }

    /**
     * Fills free seats from the queue. The caller must hold the ride's row lock.
     *
     * @return ids of the promoted students, in promotion order
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> promote(Ride ride, LocalDateTime now) {
        if (!RideStatus.EDITABLE.contains(ride.getStatus()) || ride.getAvailableSeats() == 0) {
            return List.of();
        }
        List<Long> promoted = new ArrayList<>();
        for (WaitlistEntry entry : waitlistRepository.findQueue(ride.getId())) {
            if (ride.getAvailableSeats() == 0) {
                break;
            }
            if (entry.getSeatsRequested() > ride.getAvailableSeats()) {
                continue;
            }
            User waiter = entry.getUser();
            waitlistRepository.delete(entry);

            Optional<String> reason = ineligibility(ride, waiter);
            if (reason.isPresent()) {
                log.info("Removed ineligible waiter {} from ride {}: {}", waiter.getId(), ride.getId(), reason.get());
                notificationService.notifyUsers(List.of(waiter.getId()), NotificationType.WAITLIST_REMOVED,
                        "A seat opened on %s, but %s, so you were removed from its waitlist."
                                .formatted(RideMessages.describe(ride), reason.get()), ride.getId());
                continue;
            }

            List<Long> existingMembers = participantRepository.findUserIdsByRideId(ride.getId());
            participantRepository.save(new RideParticipant(ride, waiter, entry.getSeatsRequested(),
                    ParticipantRole.MEMBER, now));
            ride.occupySeats(entry.getSeatsRequested());
            promoted.add(waiter.getId());
            log.info("Promoted waiter {} into ride {} -> {}", waiter.getId(), ride.getId(), RideMessages.seats(ride));

            notificationService.notifyUsers(List.of(waiter.getId()), NotificationType.WAITLIST_PROMOTED,
                    "Good news: a seat opened on %s and you are now in the group.".formatted(RideMessages.describe(ride)),
                    ride.getId());
            notificationService.notifyUsers(existingMembers, NotificationType.RIDE_JOINED,
                    "%s joined %s from the waitlist - %s.".formatted(waiter.getDisplayName(),
                            RideMessages.describe(ride), RideMessages.seats(ride)), ride.getId());
        }
        if (!promoted.isEmpty()) {
            participantRepository.flush();
            realtimePublisher.publishRideEvent(ride, RideEvent.RideEventType.PARTICIPANT_JOINED);
        }
        return promoted;
    }

    /** Empties the queue (ride cancelled or started) and tells every waiter why. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void clear(Ride ride, String why) {
        List<WaitlistEntry> queue = waitlistRepository.findQueue(ride.getId());
        if (queue.isEmpty()) {
            return;
        }
        waitlistRepository.deleteAll(queue);
        notificationService.notifyUsers(queue.stream().map(e -> e.getUser().getId()).toList(),
                NotificationType.WAITLIST_REMOVED,
                "You were removed from the waitlist of %s: %s.".formatted(RideMessages.describe(ride), why),
                ride.getId());
    }

    private Optional<String> ineligibility(Ride ride, User waiter) {
        if (!waiter.isActive()) {
            return Optional.of("your account is deactivated");
        }
        if (participantRepository.existsByRideIdAndUserId(ride.getId(), waiter.getId())) {
            return Optional.of("you are already a member");
        }
        if (overlapGuard.hasOverlap(waiter.getId(), ride.getDepartureAt(), List.of(ride.getId()))) {
            return Optional.of("you now have another ride around the same time");
        }
        Set<Long> blocked = blockLookup.blockedRelations(waiter.getId());
        if (participantRepository.findUserIdsByRideId(ride.getId()).stream().anyMatch(blocked::contains)) {
            // Deliberately vague: never reveal who blocked whom.
            return Optional.of("you can no longer join this group");
        }
        return Optional.empty();
    }
}
