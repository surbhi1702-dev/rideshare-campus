package com.rideshare.security;

import com.rideshare.config.AppProperties;
import com.rideshare.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and verifies HMAC-SHA256 signed access tokens.
 * The token carries only the user id (subject) and role; everything else is
 * looked up fresh on each request so deactivation takes effect immediately.
 */
@Service
public class JwtService {

    private static final String ISSUER = "rideshare-campus";
    private static final String ROLE_CLAIM = "role";

    private final SecretKey signingKey;
    private final Duration validity;

    public JwtService(AppProperties properties) {
        byte[] keyBytes = Decoders.BASE64.decode(properties.jwt().secret());
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be a Base64 encoded key of at least 256 bits");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.validity = Duration.ofMinutes(properties.jwt().expirationMinutes());
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(validity);
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .claim(ROLE_CLAIM, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /** @return the user id if the token is authentic and not expired. */
    public Optional<Long> verifyAndExtractUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.parseLong(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
