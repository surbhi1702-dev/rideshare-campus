package com.rideshare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Housekeeping jobs: idempotency key and refresh token purging, rate-limit bucket eviction. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
