package com.rideshare.common.idempotency;

import com.rideshare.common.exception.ApiException;
import com.rideshare.common.exception.ErrorCode;
import com.rideshare.common.exception.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Makes "join" safe to retry.
 *
 * <p>The client generates a random key per button press and sends it as the
 * {@code Idempotency-Key} header. If the response is lost (mobile network drop)
 * the client retries with the same key. The key is stored in the <b>same
 * transaction</b> as the join, so it exists exactly when the join committed:</p>
 * <ul>
 *   <li>key unseen: perform the operation and record the key;</li>
 *   <li>key seen for the same operation and ride: do nothing, return the current ride
 *       state (the retry gets a 200 instead of a confusing ALREADY_JOINED);</li>
 *   <li>key seen for something else: 422 IDEMPOTENCY_KEY_REUSED.</li>
 * </ul>
 * <p>Two copies of the same request arriving together are serialised by the ride's
 * row lock, which callers take before asking, so the second copy always sees the
 * first one's key.</p>
 */
@Service
public class IdempotencyService {

    public static final String HEADER = "Idempotency-Key";
    static final Duration RETENTION = Duration.ofHours(24);
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_-]{8,100}");
    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyKeyRepository repository;
    private final Clock clock;

    public IdempotencyService(IdempotencyKeyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * @param key the header value, or null when the client sent none (then nothing is deduplicated)
     * @return true if this exact request was already processed and must not run again
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean alreadyProcessed(Long userId, String key, IdempotentOperation operation, Long rideId) {
        if (key == null) {
            return false;
        }
        requireValid(key);
        return repository.findByUserIdAndKey(userId, key)
                .map(existing -> {
                    if (!existing.matches(operation, rideId)) {
                        throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                                "This Idempotency-Key was already used for a different request");
                    }
                    log.info("Replaying {} on ride {} for user {} (duplicate key)", operation, rideId, userId);
                    return true;
                })
                .orElse(false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long userId, String key, IdempotentOperation operation, Long rideId) {
        if (key != null) {
            repository.save(new IdempotencyKey(userId, key, operation, rideId, LocalDateTime.now(clock)));
        }
    }

    /** Keys only need to outlive client retries; a day is generous. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteOlderThan(LocalDateTime.now(clock).minus(RETENTION));
        if (removed > 0) {
            log.info("Purged {} expired idempotency keys", removed);
        }
    }

    private static void requireValid(String key) {
        if (!VALID_KEY.matcher(key).matches()) {
            throw new InvalidRequestException(ErrorCode.INVALID_IDEMPOTENCY_KEY,
                    "Idempotency-Key must be 8-100 characters: letters, digits, '-' or '_'");
        }
    }
}
