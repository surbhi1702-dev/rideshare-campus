package com.rideshare.auth.session;

import com.rideshare.common.exception.ApiException;
import com.rideshare.common.exception.ErrorCode;
import com.rideshare.user.User;
import com.rideshare.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Long-lived refresh tokens with rotation and reuse detection.
 *
 * <ul>
 *   <li>Every login starts a <b>family</b>. Each refresh revokes the presented token and
 *       issues a new one in the same family (rotation).</li>
 *   <li>If a token that was already rotated is presented again, someone else may hold a
 *       copy, so the whole family is revoked and both parties must log in again.</li>
 *   <li>Exception: a token rotated only seconds ago is just rejected (two tabs refreshed
 *       at the same moment); the browser already holds the newer cookie.</li>
 *   <li>Only SHA-256 hashes are stored, so a database leak does not leak sessions.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final UserRepository userRepository;
    private final Duration validity;
    private final Duration reuseGrace;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, UserRepository userRepository,
                               SessionProperties properties, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.validity = Duration.ofDays(properties.refreshTokenDays());
        this.reuseGrace = Duration.ofSeconds(properties.reuseGraceSeconds());
        this.clock = clock;
    }

    /** Starts a new session (login or registration). */
    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedRefreshToken startSession(User user) {
        return issue(user.getId(), UUID.randomUUID(), now());
    }

    /**
     * Exchanges a valid refresh token for its successor; any problem is a uniform 401.
     * No rollback on that 401: a family revoked for reuse must stay revoked.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalid();
        }
        LocalDateTime now = now();
        RefreshToken token = repository.findByHashForUpdate(hash(rawToken)).orElseThrow(RefreshTokenService::invalid);

        if (token.isRevoked()) {
            boolean justRotated = token.getRevokedReason() == RevocationReason.ROTATED
                    && token.getRevokedAt().isAfter(now.minus(reuseGrace));
            if (!justRotated) {
                int revoked = repository.revokeFamily(token.getFamilyId(), RevocationReason.REUSE_DETECTED, now);
                log.warn("Refresh token reuse for user {}: revoked {} token(s) of its session", token.getUserId(), revoked);
            }
            throw invalid();
        }
        if (token.isExpired(now)) {
            throw invalid();
        }
        User user = userRepository.findById(token.getUserId()).filter(User::isActive).orElse(null);
        if (user == null) {
            repository.revokeFamily(token.getFamilyId(), RevocationReason.LOGOUT, now);
            throw invalid();
        }
        token.revoke(RevocationReason.ROTATED, now);
        return new Rotation(user, issue(user.getId(), token.getFamilyId(), now));
    }

    /** Logout: ends the whole session the token belongs to. Unknown tokens are ignored. */
    @Transactional
    public void endSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByHashForUpdate(hash(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), RevocationReason.LOGOUT, now()));
    }

    @Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT10M")
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteExpiredBefore(now().minusDays(1));
        if (removed > 0) {
            log.info("Purged {} expired refresh tokens", removed);
        }
    }

    private IssuedRefreshToken issue(Long userId, UUID familyId, LocalDateTime now) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime expiresAt = now.plus(validity);
        repository.save(new RefreshToken(userId, hash(raw), familyId, now, expiresAt));
        return new IssuedRefreshToken(raw, validity);
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.REFRESH_TOKEN_INVALID, "Your session has expired. Please log in again.");
    }

    public record IssuedRefreshToken(String value, Duration maxAge) {
    }

    public record Rotation(User user, IssuedRefreshToken next) {
    }
}
