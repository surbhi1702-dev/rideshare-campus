package com.rideshare.ride.dto;

/** What any student may see about another student: an id and "First L." */
public record PublicUserSummary(Long id, String displayName) {
}
