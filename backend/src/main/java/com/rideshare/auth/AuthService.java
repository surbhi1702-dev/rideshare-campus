package com.rideshare.auth;

import com.rideshare.auth.dto.AuthResponse;
import com.rideshare.auth.dto.LoginRequest;
import com.rideshare.auth.dto.RegisterRequest;
import com.rideshare.auth.session.RefreshTokenService;
import com.rideshare.common.exception.AuthenticationFailedException;
import com.rideshare.common.exception.ConflictException;
import com.rideshare.common.exception.ErrorCode;
import com.rideshare.common.exception.ForbiddenOperationException;
import com.rideshare.security.JwtService;
import com.rideshare.user.EmailDomainValidator;
import com.rideshare.user.Role;
import com.rideshare.user.User;
import com.rideshare.user.UserMapper;
import com.rideshare.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailDomainValidator emailDomainValidator;
    private final UserMapper userMapper;
    private final RefreshTokenService refreshTokenService;
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       EmailDomainValidator emailDomainValidator, UserMapper userMapper,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailDomainValidator = emailDomainValidator;
        this.userMapper = userMapper;
        this.refreshTokenService = refreshTokenService;
        this.dummyHash = passwordEncoder.encode("timing-equalisation-only");
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        emailDomainValidator.requireAllowed(email);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists");
        }
        String phone = request.phoneNumber() == null || request.phoneNumber().isBlank() ? null : request.phoneNumber().trim();
        User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()), phone, Role.STUDENT);
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two simultaneous registrations with the same email: the unique index decides.
            throw new ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists");
        }
        log.info("Registered student {}", user.getId());
        return startSession(user);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email())).orElse(null);
        // Always run one BCrypt comparison so response time does not reveal whether the email exists.
        String hashToCheck = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);
        if (user == null || !passwordMatches) {
            // Same message for unknown email and wrong password: no account enumeration.
            throw new AuthenticationFailedException("Invalid email or password");
        }
        if (!user.isActive()) {
            throw new ForbiddenOperationException(ErrorCode.ACCOUNT_DISABLED,
                    "This account has been deactivated. Contact the administrator.");
        }
        return startSession(user);
    }

    /** Silent re-login from the refresh cookie: new short-lived access token plus a rotated refresh token. */
    public AuthResult refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        return new AuthResult(accessFor(rotation.user()), rotation.next());
    }

    public void logout(String refreshToken) {
        refreshTokenService.endSession(refreshToken);
    }

    private AuthResult startSession(User user) {
        return new AuthResult(accessFor(user), refreshTokenService.startSession(user));
    }

    private AuthResponse accessFor(User user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.token(), TOKEN_TYPE, token.expiresAt(), userMapper.toProfile(user));
    }

    /** The JSON body plus the refresh token the controller puts into the httpOnly cookie. */
    public record AuthResult(AuthResponse body, RefreshTokenService.IssuedRefreshToken refreshToken) {
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
