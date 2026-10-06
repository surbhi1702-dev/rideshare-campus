package com.rideshare.user;

import com.rideshare.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first ADMIN account from ADMIN_EMAIL / ADMIN_PASSWORD on startup.
 * Admins cannot self-register, and no credentials live in source code.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountInitializer(AppProperties properties, UserRepository userRepository,
                                   PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = properties.admin();
        if (!admin.isConfigured()) {
            log.info("ADMIN_EMAIL/ADMIN_PASSWORD not set - skipping admin account bootstrap");
            return;
        }
        String email = admin.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        userRepository.save(new User(admin.name(), email, passwordEncoder.encode(admin.password()), null, Role.ADMIN));
        log.info("Created admin account {}", email);
    }
}
