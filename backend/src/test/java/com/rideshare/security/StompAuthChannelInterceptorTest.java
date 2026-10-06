package com.rideshare.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StompAuthChannelInterceptorTest {

    @Test
    void onlyOwnQueueAndRideTopicsMayBeSubscribed() {
        assertThat(StompAuthChannelInterceptor.isAllowedSubscription("/user/queue/notifications")).isTrue();
        assertThat(StompAuthChannelInterceptor.isAllowedSubscription("/topic/rides/42")).isTrue();

        assertThat(StompAuthChannelInterceptor.isAllowedSubscription("/topic/rides/42/secret")).isFalse();
        assertThat(StompAuthChannelInterceptor.isAllowedSubscription("/topic/rides/abc")).isFalse();
        assertThat(StompAuthChannelInterceptor.isAllowedSubscription("/queue/notifications")).isFalse();
        assertThat(StompAuthChannelInterceptor.isAllowedSubscription("/topic/admin")).isFalse();
        assertThat(StompAuthChannelInterceptor.isAllowedSubscription(null)).isFalse();
    }
}
