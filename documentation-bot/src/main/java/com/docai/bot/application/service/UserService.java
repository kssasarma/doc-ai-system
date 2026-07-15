package com.docai.bot.application.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.auth.max-failed-attempts:10}")
    private int maxFailedAttempts;

    @Value("${app.auth.lockout-duration-minutes:15}")
    private long lockoutDurationMinutes;

    /** Returns the UUID of the currently authenticated user from the Spring Security context. */
    public UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in context");
        }
        return UUID.fromString(auth.getName());
    }

    /**
     * Brute-force protection: {@code maxFailedAttempts} consecutive bad passwords locks the
     * account for {@code lockoutDurationMinutes} — checked and enforced even if attempt N+1
     * happens to be the *correct* password, so an attacker can't "burn through" a lock by
     * guessing right on the boundary attempt. Successful login resets the counter.
     */
    @Transactional
    public User authenticate(String username, String password) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        // Same error as a wrong password — an erased account should look exactly like one that
        // never existed, not confirm to the caller that this username used to belong to someone.
        if (user.getDeletedAt() != null) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        // Unlike erasure, deactivation is a known, admin-disclosed state — telling the user why
        // they can't log in (rather than a generic "invalid credentials") is the helpful default.
        if (user.getDeactivatedAt() != null) {
            throw new IllegalArgumentException("This account has been deactivated. Contact your administrator.");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            long minutesLeft = Math.max(1, ChronoUnit.MINUTES.between(LocalDateTime.now(), user.getLockedUntil()));
            throw new AccountLockedException(
                "Too many failed login attempts. Try again in " + minutesLeft + " minute(s).");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            registerFailedAttempt(user);
            throw new IllegalArgumentException("Invalid credentials");
        }

        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        return user;
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= maxFailedAttempts) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutDurationMinutes));
            log.warn("Account {} locked for {} minutes after {} consecutive failed login attempts",
                user.getUsername(), lockoutDurationMinutes, attempts);
        }
        userRepository.save(user);
    }

    /** Verifies the current password, sets the new one, and clears {@code mustChangePassword}. */
    @Transactional
    public User changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        return userRepository.save(user);
    }
}
