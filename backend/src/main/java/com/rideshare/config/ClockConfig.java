package com.rideshare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * A single injectable clock in institute local time. Every "is this in the past?"
 * decision goes through it, which keeps time rules consistent and testable.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(AppProperties properties) {
        return Clock.system(ZoneId.of(properties.timeZone()));
    }
}
