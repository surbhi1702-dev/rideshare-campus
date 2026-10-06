package com.rideshare.security;

import com.rideshare.user.Role;
import com.rideshare.user.User;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * Minimal principal stored in the SecurityContext. Its name is the user id,
 * which is also the key used for per-user WebSocket destinations.
 */
public record AuthenticatedUser(Long id, String email, Role role) implements AuthenticatedPrincipal {

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
    }

    @Override
    public String getName() {
        return String.valueOf(id);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
