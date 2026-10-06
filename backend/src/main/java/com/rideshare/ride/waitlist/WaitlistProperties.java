package com.rideshare.ride.waitlist;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code app.waitlist.*} */
@Validated
@ConfigurationProperties(prefix = "app.waitlist")
public record WaitlistProperties(@Positive int maxSize) {
}
