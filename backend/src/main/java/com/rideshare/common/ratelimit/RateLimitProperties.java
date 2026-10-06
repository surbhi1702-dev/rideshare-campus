package com.rideshare.common.ratelimit;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.rate-limit.*}
 *
 * @param enabled        switch off for load tests and the race demo
 * @param authPerMinute  login / register attempts per client IP
 * @param joinPerMinute  join and waitlist requests per signed-in user
 */
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(boolean enabled, @Positive int authPerMinute, @Positive int joinPerMinute) {
}
