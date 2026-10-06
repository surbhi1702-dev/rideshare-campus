package com.rideshare.security;

import com.rideshare.user.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Turns a raw bearer token into a Spring Authentication. Shared by the HTTP
 * filter and the STOMP (WebSocket) CONNECT interceptor.
 */
@Component
public class TokenAuthenticator {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public TokenAuthenticator(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    public Optional<UsernamePasswordAuthenticationToken> authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return jwtService.verifyAndExtractUserId(token)
                .flatMap(userRepository::findById)
                .filter(user -> user.isActive())
                .map(user -> {
                    AuthenticatedUser principal = AuthenticatedUser.from(user);
                    return new UsernamePasswordAuthenticationToken(principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                });
    }
}
