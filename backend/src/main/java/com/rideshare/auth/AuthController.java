package com.rideshare.auth;

import com.rideshare.auth.dto.AuthResponse;
import com.rideshare.auth.dto.LoginRequest;
import com.rideshare.auth.dto.RegisterRequest;
import com.rideshare.auth.session.RefreshCookies;
import com.rideshare.common.exception.ErrorCode;
import com.rideshare.common.exception.ForbiddenOperationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sessions: a short-lived access token in the JSON body (the frontend keeps it in
 * memory only) and a long-lived refresh token in an httpOnly cookie.
 *
 * <p>The cookie is sent automatically by the browser, so the two endpoints that read
 * it (refresh, logout) also require an {@code X-Requested-With} header. A plain
 * cross-site form or link cannot add custom headers, and a cross-origin script that
 * tries is stopped by the CORS preflight - a cheap CSRF defence on top of SameSite.</p>
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
@SecurityRequirements
public class AuthController {

    static final String CSRF_HEADER = "X-Requested-With";

    private final AuthService authService;
    private final RefreshCookies refreshCookies;

    public AuthController(AuthService authService, RefreshCookies refreshCookies) {
        this.authService = authService;
        this.refreshCookies = refreshCookies;
    }

    @PostMapping("/register")
    @Operation(summary = "Register with an institute email",
            description = "201 with an access token; sets the refresh cookie. 400 INVALID_EMAIL_DOMAIN, "
                    + "409 EMAIL_ALREADY_REGISTERED.")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withCookie(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in: access token in the body, refresh token in an httpOnly cookie",
            description = "401 INVALID_CREDENTIALS for wrong email/password, 403 ACCOUNT_DISABLED for deactivated accounts.")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withCookie(HttpStatus.OK, authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Get a new access token using the refresh cookie (rotates the cookie)",
            description = "Requires the X-Requested-With header. 401 REFRESH_TOKEN_INVALID when the session "
                    + "expired, was logged out or a reused token was detected.")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request,
                                                @RequestHeader(name = CSRF_HEADER, required = false) String csrf) {
        requireCsrfHeader(csrf);
        return withCookie(HttpStatus.OK, authService.refresh(refreshCookies.read(request)));
    }

    @PostMapping("/logout")
    @Operation(summary = "End the session and clear the refresh cookie", description = "Requires X-Requested-With.")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                       @RequestHeader(name = CSRF_HEADER, required = false) String csrf) {
        requireCsrfHeader(csrf);
        authService.logout(refreshCookies.read(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookies.clear()).build();
    }

    private ResponseEntity<AuthResponse> withCookie(HttpStatus status, AuthService.AuthResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookies.set(result.refreshToken()))
                .body(result.body());
    }

    private static void requireCsrfHeader(String value) {
        if (value == null || value.isBlank()) {
            throw new ForbiddenOperationException(ErrorCode.ACCESS_DENIED, "Missing X-Requested-With header");
        }
    }
}
