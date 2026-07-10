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
import com.docai.bot.domain.repository.TenantMembershipRepository;
import com.docai.bot.domain.repository.UserRepository;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * All non-bootstrap accounts are provisioned by invitation — there is no public self-registration.
 * A SUPER_ADMIN invites a tenant's first ADMIN; a tenant ADMIN invites their tenant's USERs.
 *
 * <p>An invited email may already belong to an existing identity elsewhere in the system (a
 * person who's a member of one tenant being invited into a second) — {@link #invite} allows that
 * as long as they aren't already a member of *this* tenant, and {@link #accept} detects it by
 * looking the email back up rather than assuming every accepted invitation creates a brand new
 * account.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationService {

    private static final int TOKEN_BYTES = 32;
    private static final long EXPIRY_HOURS = 72;

    private final InvitationTokenRepository invitationRepository;
    private final UserRepository userRepository;
    private final TenantMembershipRepository membershipRepository;
    private final TenantMembershipService membershipService;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final DigestProperties props;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public InvitationToken invite(String email, User.Role role, UUID tenantId, UUID invitedBy) {
        if (role != User.Role.SUPER_ADMIN && tenantId == null) {
            throw new IllegalArgumentException("tenantId is required for role " + role);
        }

        userRepository.findByEmail(email).ifPresent(existing -> {
            if (tenantId != null && membershipRepository.existsByUserIdAndTenantId(existing.getId(), tenantId)) {
                throw new IllegalArgumentException("This person is already a member of this tenant");
            }
        });

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

        User user = userRepository.findByEmail(invitation.getEmail())
            .map(existing -> joinExistingIdentityToTenant(existing, username, password, invitation))
            .orElseGet(() -> provisionNewIdentity(username, password, invitation));

        invitation.setAcceptedAt(LocalDateTime.now());
        invitationRepository.save(invitation);
        return user;
    }

    /**
     * The invited email already has an account (elsewhere, in another tenant) — {@code username}
     * and {@code password} here verify that the person accepting is that account's owner, not
     * set a new password. On success the new tenant becomes their active tenant/role, matching
     * the "you just joined a new workspace" expectation.
     */
    private User joinExistingIdentityToTenant(User existing, String username, String password, InvitationToken invitation) {
        if (!existing.getUsername().equals(username) || !passwordEncoder.matches(password, existing.getPasswordHash())) {
            throw new IllegalArgumentException("Incorrect username or password for the existing account with this email");
        }
        if (invitation.getTenantId() != null
                && membershipRepository.existsByUserIdAndTenantId(existing.getId(), invitation.getTenantId())) {
            throw new IllegalStateException("This person is already a member of this tenant");
        }

        if (invitation.getTenantId() != null) {
            membershipService.ensureMembership(existing.getId(), invitation.getTenantId(), invitation.getRole());
        }
        existing.setTenantId(invitation.getTenantId());
        existing.setRole(invitation.getRole());
        User saved = userRepository.save(existing);
        log.info("Existing identity '{}' joined tenant {} with role {}", username, invitation.getTenantId(), invitation.getRole());
        return saved;
    }

    private User provisionNewIdentity(String username, String password, InvitationToken invitation) {
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

        if (invitation.getTenantId() != null) {
            membershipService.ensureMembership(user.getId(), invitation.getTenantId(), invitation.getRole());
        }

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
