package com.docai.bot.application.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.config.DigestProperties;
import com.docai.bot.domain.entity.InvitationToken;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.InvitationTokenRepository;
import com.docai.bot.domain.repository.UserRepository;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * All non-bootstrap accounts are provisioned by invitation — there is no public self-registration.
 * A SUPER_ADMIN invites a tenant's first ADMIN; a tenant ADMIN invites their tenant's USERs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationService {

    private static final int TOKEN_BYTES = 32;
    private static final long EXPIRY_HOURS = 72;

    private final InvitationTokenRepository invitationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final DigestProperties props;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public InvitationToken invite(String email, User.Role role, UUID tenantId, UUID invitedBy) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("A user with this email already exists");
        }
        if (role != User.Role.SUPER_ADMIN && tenantId == null) {
            throw new IllegalArgumentException("tenantId is required for role " + role);
        }

        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        InvitationToken invitation = invitationRepository.save(InvitationToken.builder()
            .token(token)
            .email(email)
            .role(role)
            .tenantId(tenantId)
            .invitedBy(invitedBy)
            .expiresAt(LocalDateTime.now().plusHours(EXPIRY_HOURS))
            .build());

        sendInviteEmail(invitation);
        return invitation;
    }

    @Transactional
    public User accept(String token, String username, String password) {
        InvitationToken invitation = invitationRepository.findByToken(token)
            .orElseThrow(() -> new IllegalArgumentException("Invalid invitation"));

        if (invitation.isAccepted()) {
            throw new IllegalStateException("This invitation has already been used");
        }
        if (invitation.isExpired()) {
            throw new IllegalStateException("This invitation has expired");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken");
        }

        User user = userRepository.save(User.builder()
            .username(username)
            .email(invitation.getEmail())
            .passwordHash(passwordEncoder.encode(password))
            .role(invitation.getRole())
            .tenantId(invitation.getTenantId())
            .build());

        invitation.setAcceptedAt(LocalDateTime.now());
        invitationRepository.save(invitation);

        log.info("Invitation accepted: '{}' provisioned with role {}", username, invitation.getRole());
        return user;
    }

    private void sendInviteEmail(InvitationToken invitation) {
        try {
            String link = props.getAppUrl() + "/accept-invite?token=" + invitation.getToken();

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(props.getFromAddress(), props.getFromName());
            helper.setTo(invitation.getEmail());
            helper.setSubject("You've been invited to " + props.getFromName());
            helper.setText("""
                <!DOCTYPE html>
                <html><body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; padding: 24px;">
                <div style="max-width: 480px; margin: 0 auto;">
                  <h2>You've been invited</h2>
                  <p>You've been invited to join %s as a <strong>%s</strong>.</p>
                  <p><a href="%s" style="display:inline-block;padding:10px 20px;background:#1d4ed8;color:#fff;
                     border-radius:8px;text-decoration:none;font-weight:600;">Accept Invitation</a></p>
                  <p style="color:#64748b;font-size:12px;">This link expires in 72 hours. If you weren't expecting this, you can ignore this email.</p>
                </div>
                </body></html>
                """.formatted(props.getFromName(), invitation.getRole().name(), link), true);
            mailSender.send(message);
        } catch (Exception e) {
            // Do not fail invitation creation over a transient mail-server issue — the invite row/token
            // still exists and can be resent or shared manually.
            log.error("Failed to send invitation email to {}: {}", invitation.getEmail(), e.getMessage());
        }
    }
}
