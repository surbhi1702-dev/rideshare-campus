package com.rideshare.auth.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

/**
 * Builds the refresh cookie: httpOnly (JavaScript can never read it, so an XSS bug
 * cannot steal the session), scoped to /api/auth (not sent with ordinary API calls),
 * Secure and SameSite from configuration.
 */
@Component
public class RefreshCookies {

    static final String PATH = "/api/auth";

    private final SessionProperties properties;

    public RefreshCookies(SessionProperties properties) {
        this.properties = properties;
    }

    public String set(RefreshTokenService.IssuedRefreshToken token) {
        return build(token.value(), token.maxAge());
    }

    public String clear() {
        return build("", Duration.ZERO);
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> properties.cookieName().equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path(PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
