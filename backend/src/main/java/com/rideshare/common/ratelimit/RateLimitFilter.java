package com.rideshare.common.ratelimit;

import com.rideshare.common.exception.ErrorCode;
import com.rideshare.security.AuthenticatedUser;
import com.rideshare.security.JsonErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Applies {@link RateLimiter} to the endpoints worth protecting:
 * <ul>
 *   <li>login / register, per client IP: slows password guessing and sign-up spam;</li>
 *   <li>join / waitlist, per user: stops a script from hammering seat allocation.</li>
 * </ul>
 * Runs inside the security chain after JWT authentication, so the user is known.
 * The client IP is {@code request.getRemoteAddr()}; behind a proxy set
 * {@code FORWARD_HEADERS_STRATEGY=framework} so it reflects X-Forwarded-For.
 *
 * <p>Refresh is deliberately not limited: every page load calls it, many students
 * share one campus NAT address, and a refresh token cannot be guessed anyway.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> AUTH_PATHS = Set.of("/api/auth/login", "/api/auth/register");
    private static final Pattern SEAT_PATH = Pattern.compile("^/api/rides/\\d+/(join|waitlist)$");

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final JsonErrorWriter errorWriter;

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties, JsonErrorWriter errorWriter) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || !"POST".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        RateLimiter.Decision decision = null;
        if (AUTH_PATHS.contains(path)) {
            decision = rateLimiter.tryAcquire("auth:" + request.getRemoteAddr(), properties.authPerMinute());
        } else if (SEAT_PATH.matcher(path).matches() && currentUserId() != null) {
            decision = rateLimiter.tryAcquire("join:" + currentUserId(), properties.joinPerMinute());
        }
        if (decision != null && !decision.allowed()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
            errorWriter.write(request, response, ErrorCode.RATE_LIMITED,
                    "Too many requests. Try again in %d seconds.".formatted(decision.retryAfterSeconds()));
            return;
        }
        chain.doFilter(request, response);
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                ? user.id() : null;
    }
}
