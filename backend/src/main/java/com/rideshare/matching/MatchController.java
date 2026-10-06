package com.rideshare.matching;

import com.rideshare.matching.dto.MatchSearchRequest;
import com.rideshare.matching.dto.RideMatchResponse;
import com.rideshare.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rides")
@Tag(name = "Matching", description = "Ranked ride suggestions from the matching engine")
public class MatchController {

    private final MatchingService matchingService;

    public MatchController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @GetMapping("/search")
    @Operation(summary = "Find rides matching a journey",
            description = "Rides within MAX_PICKUP_DISTANCE_KM of the pickup, MAX_DESTINATION_DISTANCE_KM of the drop "
                    + "and MAX_TIME_DIFFERENCE_MINUTES of the time, with enough free seats, best match first.")
    public List<RideMatchResponse> search(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                          @Valid @ParameterObject @ModelAttribute MatchSearchRequest request) {
        return matchingService.search(request, currentUser.id());
    }

    @GetMapping("/{id}/matches")
    @Operation(summary = "Rides compatible with one of my rides",
            description = "Only members of the ride may call this (403 otherwise).")
    public List<RideMatchResponse> matches(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                           @PathVariable Long id) {
        return matchingService.matchesForRide(id, currentUser.id());
    }
}
