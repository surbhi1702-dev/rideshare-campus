package com.rideshare.matching;

import com.rideshare.notification.NotificationService;
import com.rideshare.notification.NotificationType;
import com.rideshare.ride.Ride;
import com.rideshare.ride.RideMessages;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * When a new ride is posted, tell the creators of compatible existing rides
 * about it ("reverse matching"), so two half-empty cabs can merge into one.
 */
@Component
public class MatchSuggestionNotifier {

    private final MatchingEngine matchingEngine;
    private final NotificationService notificationService;
    private final MatchingProperties properties;

    public MatchSuggestionNotifier(MatchingEngine matchingEngine, NotificationService notificationService,
                                   MatchingProperties properties) {
        this.matchingEngine = matchingEngine;
        this.notificationService = notificationService;
        this.properties = properties;
    }

    /** Runs inside the creating transaction; notifications are pushed after commit. */
    public void notifyOwnersOfSimilarRides(Ride newRide) {
        MatchQuery query = new MatchQuery(newRide.getCreator().getId(), newRide.getSource(), newRide.getDestination(),
                newRide.getDepartureAt(), 1, newRide.getId());

        Set<Long> ownersToNotify = new LinkedHashSet<>();
        for (MatchResult match : matchingEngine.findMatches(query)) {
            ownersToNotify.add(match.ride().getCreator().getId());
            if (ownersToNotify.size() >= properties.suggestionNotificationLimit()) {
                break;
            }
        }
        notificationService.notifyUsers(ownersToNotify, NotificationType.MATCH_SUGGESTION,
                "A new ride similar to yours was posted: %s by %s. Consider travelling together to split the fare."
                        .formatted(RideMessages.describe(newRide), newRide.getCreator().getDisplayName()),
                newRide.getId());
    }
}
