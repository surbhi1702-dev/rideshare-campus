package com.rideshare.common.ratelimit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory token bucket per key (e.g. "auth:10.0.0.7" or "join:42").
 *
 * <p>Each bucket holds up to {@code perMinute} tokens and refills continuously at
 * {@code perMinute} tokens per minute, so short bursts are allowed but the
 * sustained rate is capped. A request takes one token or is rejected with the
 * time until the next token arrives (sent as {@code Retry-After}).</p>
 *
 * <p>State lives in this JVM, which is right for a single instance. With several
 * instances each would count separately; the usual fix is the same algorithm in
 * Redis (one shared counter per key).</p>
 */
@Component
public class RateLimiter {

    private static final long NANOS_PER_MINUTE = Duration.ofMinutes(1).toNanos();
    private static final long IDLE_EVICTION_NANOS = Duration.ofMinutes(10).toNanos();

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier nanoClock;

    public RateLimiter() {
        this(System::nanoTime);
    }

    RateLimiter(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    public Decision tryAcquire(String key, int perMinute) {
        long now = nanoClock.getAsLong();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(perMinute, now));
        return bucket.take(perMinute, now);
    }

    /** Buckets untouched for 10 minutes are full again anyway; dropping them bounds memory. */
    @Scheduled(fixedDelayString = "PT5M")
    public void evictIdle() {
        long now = nanoClock.getAsLong();
        buckets.values().removeIf(bucket -> bucket.idleFor(now) > IDLE_EVICTION_NANOS);
    }

    int trackedKeys() {
        return buckets.size();
    }

    /** @param retryAfterSeconds 0 when allowed */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefill;

        Bucket(int capacity, long now) {
            this.tokens = capacity;
            this.lastRefill = now;
        }

        synchronized Decision take(int capacity, long now) {
            double refill = (now - lastRefill) * (double) capacity / NANOS_PER_MINUTE;
            tokens = Math.min(capacity, tokens + refill);
            lastRefill = now;
            if (tokens >= 1) {
                tokens -= 1;
                return new Decision(true, 0);
            }
            double nanosUntilToken = (1 - tokens) * NANOS_PER_MINUTE / capacity;
            return new Decision(false, Math.max(1, (long) Math.ceil(nanosUntilToken / 1_000_000_000d)));
        }

        synchronized long idleFor(long now) {
            return now - lastRefill;
        }
    }
}
