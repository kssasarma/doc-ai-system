package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.docai.bot.application.service.AccountLockedException;
import com.docai.bot.application.service.UserService;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserService userService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID   = UUID.randomUUID();
    private static final String RAW_PW  = "correct-password-123";

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(userService, "maxFailedAttempts", 3);
        ReflectionTestUtils.setField(userService, "lockoutDurationMinutes", 15L);
    }

    // ── authenticate ──────────────────────────────────────────────────────────

    @Test
    void authenticate_validCredentials_returnsUser() {
        User user = activeUser(RAW_PW);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        // No save() stub: service skips the counter reset when failedLoginAttempts is already 0

        User result = userService.authenticate("alice", RAW_PW);

        assertThat(result.getUsername()).isEqualTo("alice");
    }

    @Test
    void authenticate_wrongPassword_throwsIllegalArgument() {
        User user = activeUser(RAW_PW);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        assertThatThrownBy(() -> userService.authenticate("alice", "wrong-password"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid credentials");
    }

    @Test
    void authenticate_unknownUsername_throwsIllegalArgument() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.authenticate("nobody", RAW_PW))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid credentials");
    }

    @Test
    void authenticate_deletedUser_throwsIllegalArgument() {
        User user = activeUser(RAW_PW);
        user.setDeletedAt(LocalDateTime.now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.authenticate("alice", RAW_PW))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid credentials");
    }

    @Test
    void authenticate_deactivatedUser_throwsIllegalArgument() {
        User user = activeUser(RAW_PW);
        user.setDeactivatedAt(LocalDateTime.now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.authenticate("alice", RAW_PW))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("deactivated");
    }

    @Test
    void authenticate_lockedAccount_throwsAccountLockedException() {
        User user = activeUser(RAW_PW);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.authenticate("alice", RAW_PW))
            .isInstanceOf(AccountLockedException.class)
            .hasMessageContaining("Too many failed login attempts");
    }

    @Test
    void authenticate_exceedsMaxAttempts_locksAccount() {
        User user = activeUser(RAW_PW);
        // Simulate 2 prior failures
        user.setFailedLoginAttempts(2);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(savedUser.capture())).thenReturn(user);

        assertThatThrownBy(() -> userService.authenticate("alice", "wrong"))
            .isInstanceOf(IllegalArgumentException.class);

        // Third failure should trigger lockout
        User captured = savedUser.getValue();
        assertThat(captured.getLockedUntil()).isAfter(LocalDateTime.now());
        assertThat(captured.getFailedLoginAttempts()).isEqualTo(3);
    }

    @Test
    void authenticate_successAfterPriorFailures_resetsCounter() {
        User user = activeUser(RAW_PW);
        user.setFailedLoginAttempts(2);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(savedUser.capture())).thenReturn(user);

        userService.authenticate("alice", RAW_PW);

        assertThat(savedUser.getValue().getFailedLoginAttempts()).isEqualTo(0);
        assertThat(savedUser.getValue().getLockedUntil()).isNull();
    }

    // ── changePassword ────────────────────────────────────────────────────────

    @Test
    void changePassword_correctCurrentPassword_updatesHash() {
        User user = activeUser(RAW_PW);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(savedUser.capture())).thenReturn(user);

        userService.changePassword(USER_ID, RAW_PW, "new-super-secure-password-99");

        User saved = savedUser.getValue();
        assertThat(passwordEncoder.matches("new-super-secure-password-99", saved.getPasswordHash())).isTrue();
        assertThat(saved.isMustChangePassword()).isFalse();
    }

    @Test
    void changePassword_incorrectCurrentPassword_throwsIllegalArgument() {
        User user = activeUser(RAW_PW);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changePassword(USER_ID, "wrong-pw", "new-super-secure-password-99"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Current password is incorrect");
    }

    @Test
    void changePassword_unknownUserId_throwsIllegalArgument() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword(USER_ID, RAW_PW, "new-super-secure-password-99"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User not found");
    }

    @Test
    void changePassword_clearsMustChangePasswordFlag() {
        User user = activeUser(RAW_PW);
        user.setMustChangePassword(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(savedUser.capture())).thenReturn(user);

        userService.changePassword(USER_ID, RAW_PW, "new-super-secure-password-99");

        assertThat(savedUser.getValue().isMustChangePassword()).isFalse();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User activeUser(String rawPassword) {
        return User.builder()
            .id(USER_ID)
            .username("alice")
            .email("alice@example.com")
            .passwordHash(passwordEncoder.encode(rawPassword))
            .role(User.Role.ADMIN)
            .tenantId(TENANT_ID)
            .mustChangePassword(false)
            .failedLoginAttempts(0)
            .build();
    }
}
