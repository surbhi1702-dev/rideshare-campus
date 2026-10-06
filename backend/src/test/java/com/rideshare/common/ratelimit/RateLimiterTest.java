package com.rideshare.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final AtomicLong now = new AtomicLong(0);
    private final RateLimiter limiter = new RateLimiter(now::get);

    @Test
    void allowsABurstUpToTheLimitThenRejectsWithRetryAfter() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("k", 3).allowed()).isTrue();
        }
        RateLimiter.Decision rejected = limiter.tryAcquire("k", 3);
        assertThat(rejected.allowed()).isFalse();
        // 3 per minute = one token every 20 s.
        assertThat(rejected.retryAfterSeconds()).isEqualTo(20);
    }

    @Test
    void tokensRefillOverTime() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("k", 3);
        }
        now.addAndGet(Duration.ofSeconds(20).toNanos());
        assertThat(limiter.tryAcquire("k", 3).allowed()).isTrue();
        assertThat(limiter.tryAcquire("k", 3).allowed()).isFalse();
    }

    @Test
    void keysAreIndependent() {
        limiter.tryAcquire("a", 1);
        assertThat(limiter.tryAcquire("a", 1).allowed()).isFalse();
        assertThat(limiter.tryAcquire("b", 1).allowed()).isTrue();
    }

    @Test
    void idleBucketsAreEvicted() {
        limiter.tryAcquire("a", 1);
        now.addAndGet(Duration.ofMinutes(11).toNanos());
        limiter.evictIdle();
        assertThat(limiter.trackedKeys()).isZero();
    }
}
