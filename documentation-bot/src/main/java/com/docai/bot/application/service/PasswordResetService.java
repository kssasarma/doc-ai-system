package com.docai.bot.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.config.DigestProperties;
import com.docai.bot.domain.entity.PasswordResetToken;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.PasswordResetTokenRepository;
import com.docai.bot.domain.repository.UserRepository;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * "Forgot password" self-service flow for invite-only accounts — until this existed, a user who
 * forgot their password had no recovery path at all (change-password requires the *current*
 * password). Every response is shaped to avoid confirming whether a given email has an account
 * (see {@link #requestReset}): the token is single-use and hashed at rest (same rationale as
 * {@link InvitationService}), expiring after one hour.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final long EXPIRY_HOURS = 1;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final JavaMailSender mailSender;
    private final DigestProperties props;
    private final AuditLogService auditLogService;
    private final SecureRandom random = new SecureRandom();

    /** Always "succeeds" from the caller's point of view regardless of whether {@code email}
     * matches an account — the controller returns 200 either way. Only emails a link and writes
     * an audit row when it does match. */
    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            byte[] bytes = new byte[TOKEN_BYTES];
            random.nextBytes(bytes);
            String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            resetTokenRepository.save(PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(hash(rawToken))
                .expiresAt(LocalDateTime.now().plusHours(EXPIRY_HOURS))
                .build());

            sendResetEmail(user, rawToken);
            auditLogService.log(user.getId(), user.getTenantId(), "PASSWORD_RESET_REQUESTED", "USER", user.getId(), null, null);
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = resetTokenRepository.findByTokenHash(hash(rawToken))
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset link"));
        if (token.isUsed() || token.isExpired()) {
            throw new IllegalArgumentException("Invalid or expired reset link");
        }
        User user = userRepository.findById(token.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset link"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);

        token.setUsedAt(LocalDateTime.now());
        resetTokenRepository.save(token);

        // A password reset is exactly the moment to force every other device/session to
        // re-authenticate — same reasoning as AuthController#changePassword.
        refreshTokenService.revokeAllForUser(user.getId());
        auditLogService.log(user.getId(), user.getTenantId(), "PASSWORD_RESET_COMPLETED", "USER", user.getId(), null, null);
    }

    private void sendResetEmail(User user, String rawToken) {
        try {
            String link = props.getAppUrl() + "/reset-password?token=" + rawToken;

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(props.getFromAddress(), props.getFromName());
            helper.setTo(user.getEmail());
            helper.setSubject("Reset your password");
            helper.setText("""
                <!DOCTYPE html>
                <html><body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; padding: 24px;">
                <div style="max-width: 480px; margin: 0 auto;">
                  <h2>Reset your password</h2>
                  <p>Someone (hopefully you) requested a password reset for your %s account.</p>
                  <p><a href="%s" style="display:inline-block;padding:10px 20px;background:#1d4ed8;color:#fff;
                     border-radius:8px;text-decoration:none;font-weight:600;">Reset Password</a></p>
                  <p style="color:#64748b;font-size:12px;">This link expires in 1 hour and can only be used once.
                     If you didn't request this, you can safely ignore this email.</p>
                </div>
                </body></html>
                """.formatted(props.getFromName(), link), true);
            mailSender.send(message);
        } catch (Exception e) {
            // Same reasoning as InvitationService: don't fail the request over a transient
            // mail-server issue — nothing the caller can act on differently either way, since the
            // response is identical regardless of success (no user-enumeration).
            log.error("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
