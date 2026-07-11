package com.docai.bot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Replaces the old public {@code /api/auth/bootstrap} endpoint: on a genuinely empty database,
 * seeds a single fixed SUPER_ADMIN account instead of letting whoever loads the UI first claim it.
 * The seeded account has {@code mustChangePassword=true}, so login is only usable to set a real
 * password — see {@link com.docai.bot.config.JwtAuthFilter} for how that's enforced, and
 * {@code AuthController#changePassword} for the one-time reset.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed-admin.username}")
    private String seedUsername;

    @Value("${app.seed-admin.email}")
    private String seedEmail;

    @Value("${app.seed-admin.password}")
    private String seedPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }

        User admin = User.builder()
            .username(seedUsername)
            .email(seedEmail)
            .passwordHash(passwordEncoder.encode(seedPassword))
            .role(User.Role.SUPER_ADMIN)
            .tenantId(null)
            .mustChangePassword(true)
            .build();

        userRepository.save(admin);
        log.info("Seeded initial SUPER_ADMIN account '{}' — password change required on first login", seedUsername);
    }
}
