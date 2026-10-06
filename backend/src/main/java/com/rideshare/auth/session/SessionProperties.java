package com.rideshare.auth.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.session.*} - refresh token lifetime and cookie attributes.
 *
 * @param refreshTokenDays   how long a login lasts without activity
 * @param cookieName         name of the httpOnly refresh cookie
 * @param cookieSecure       send the cookie over HTTPS only (true in production)
 * @param cookieSameSite     Lax when frontend and API share a site (default, via proxy);
 *                           None (requires Secure) when they are on different sites
 * @param reuseGraceSeconds  a token rotated less than this long ago is rejected without
 *                           revoking the whole session (two tabs refreshing at once)
 */
@Validated
@ConfigurationProperties(prefix = "app.session")
public record SessionProperties(
        @Positive int refreshTokenDays,
        @NotBlank String cookieName,
        boolean cookieSecure,
        @Pattern(regexp = "Strict|Lax|None") String cookieSameSite,
        @PositiveOrZero int reuseGraceSeconds
) {
}
