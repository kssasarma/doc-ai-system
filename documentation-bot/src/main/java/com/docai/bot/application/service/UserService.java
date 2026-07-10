package com.docai.bot.application.service;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * One-time first-run bootstrap: the very first account on a fresh install becomes SUPER_ADMIN
     * (not scoped to any tenant). Fails once any account exists — every subsequent account is
     * provisioned via {@link InvitationService}.
     */
    @Transactional
    public User bootstrapSuperAdmin(String username, String email, String password) {
        if (userRepository.count() > 0) {
            throw new IllegalStateException("Bootstrap already completed — use an invitation instead");
        }

        User user = User.builder()
            .username(username)
            .email(email)
            .passwordHash(passwordEncoder.encode(password))
            .role(User.Role.SUPER_ADMIN)
            .tenantId(null)
            .build();

        User saved = userRepository.save(user);
        log.info("Bootstrapped first SUPER_ADMIN account '{}'", username);
        return saved;
    }

    /** Returns the UUID of the currently authenticated user from the Spring Security context. */
    public UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in context");
        }
        return UUID.fromString(auth.getName());
    }

    public User authenticate(String username, String password) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        return user;
    }
}
