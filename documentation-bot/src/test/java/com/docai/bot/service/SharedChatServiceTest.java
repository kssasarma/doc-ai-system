package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.docai.bot.application.service.SharedChatService;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.entity.ChatSession;
import com.docai.bot.domain.entity.SharedChatLink;
import com.docai.bot.domain.repository.ChatMessageRepository;
import com.docai.bot.domain.repository.ChatSessionRepository;
import com.docai.bot.domain.repository.SharedChatLinkRepository;
import com.docai.bot.domain.repository.UserRepository;

/**
 * Covers the Phase 5 security fix: a "team only" (non-public) share link must actually be
 * enforced, and only the owning session's user may create a link for it in the first place.
 */
@ExtendWith(MockitoExtension.class)
class SharedChatServiceTest {

    @Mock SharedChatLinkRepository linkRepository;
    @Mock ChatSessionRepository sessionRepository;
    @Mock ChatMessageRepository messageRepository;
    @Mock UserRepository userRepository;

    private SharedChatService service;

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID chatId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SharedChatService(linkRepository, sessionRepository, messageRepository, userRepository);
    }

    private ChatSession ownerSession() {
        return ChatSession.builder().id(chatId).userId(ownerId).tenantId(tenantA).messageCount(0).build();
    }

    private SharedChatLink link(boolean publicAccess, UUID tenantId) {
        return SharedChatLink.builder()
            .id(UUID.randomUUID())
            .chatId(chatId)
            .token("tok-123")
            .createdBy(ownerId)
            .tenantId(tenantId)
            .publicAccess(publicAccess)
            .build();
    }

    // ── createShareLink: ownership must be checked ─────────────────────────

    @Test
    void createShareLink_ownerOfSession_succeedsAndCopiesSessionTenant() {
        when(sessionRepository.findById(chatId)).thenReturn(Optional.of(ownerSession()));
        when(linkRepository.findByChatId(chatId)).thenReturn(Optional.empty());
        when(linkRepository.save(any(SharedChatLink.class))).thenAnswer(inv -> inv.getArgument(0));

        UserPrincipal owner = new UserPrincipal(ownerId, "owner", "USER", tenantA, false);
        SharedChatService.ShareLinkDTO dto = service.createShareLink(chatId, owner, true, null);

        assertThat(dto.isPublicAccess()).isTrue();
        assertThat(dto.getChatId()).isEqualTo(chatId.toString());
    }

    @Test
    void createShareLink_nonOwner_isRejected() {
        when(sessionRepository.findById(chatId)).thenReturn(Optional.of(ownerSession()));

        UserPrincipal intruder = new UserPrincipal(UUID.randomUUID(), "not-the-owner", "USER", tenantA, false);

        assertThatThrownBy(() -> service.createShareLink(chatId, intruder, true, null))
            .isInstanceOf(AccessDeniedException.class);

        verify(linkRepository, never()).save(any());
    }

    // ── getSharedChat: publicAccess must actually gate the read ────────────

    @Test
    void getSharedChat_publicLink_viewableByAnonymousVisitor() {
        SharedChatLink link = link(true, tenantA);
        when(linkRepository.findByToken("tok-123")).thenReturn(Optional.of(link));
        when(sessionRepository.findById(chatId)).thenReturn(Optional.of(ownerSession()));
        when(messageRepository.findByChatIdOrderByCreatedAtAsc(chatId)).thenReturn(List.of());
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());

        SharedChatService.SharedChatViewDTO dto = service.getSharedChat("tok-123", null);

        assertThat(dto.getChatId()).isEqualTo(chatId.toString());
    }

    @Test
    void getSharedChat_nonPublicLink_anonymousVisitor_isRejected() {
        SharedChatLink link = link(false, tenantA);
        when(linkRepository.findByToken("tok-123")).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.getSharedChat("tok-123", null))
            .isInstanceOf(AccessDeniedException.class);

        verify(sessionRepository, never()).findById(any());
    }

    @Test
    void getSharedChat_nonPublicLink_differentTenant_isRejected() {
        SharedChatLink link = link(false, tenantA);
        when(linkRepository.findByToken("tok-123")).thenReturn(Optional.of(link));

        UserPrincipal outsider = new UserPrincipal(UUID.randomUUID(), "outsider", "USER", tenantB, false);

        assertThatThrownBy(() -> service.getSharedChat("tok-123", outsider))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getSharedChat_nonPublicLink_sameTenant_isAllowed() {
        SharedChatLink link = link(false, tenantA);
        when(linkRepository.findByToken("tok-123")).thenReturn(Optional.of(link));
        when(sessionRepository.findById(chatId)).thenReturn(Optional.of(ownerSession()));
        when(messageRepository.findByChatIdOrderByCreatedAtAsc(chatId)).thenReturn(List.of());
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());

        UserPrincipal teammate = new UserPrincipal(UUID.randomUUID(), "teammate", "USER", tenantA, false);

        SharedChatService.SharedChatViewDTO dto = service.getSharedChat("tok-123", teammate);

        assertThat(dto.getChatId()).isEqualTo(chatId.toString());
    }

    @Test
    void getSharedChat_nonPublicLink_creatorCanAlwaysViewOwnLink() {
        SharedChatLink link = link(false, tenantA);
        when(linkRepository.findByToken("tok-123")).thenReturn(Optional.of(link));
        when(sessionRepository.findById(chatId)).thenReturn(Optional.of(ownerSession()));
        when(messageRepository.findByChatIdOrderByCreatedAtAsc(chatId)).thenReturn(List.of());
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());

        UserPrincipal owner = new UserPrincipal(ownerId, "owner", "USER", tenantA, false);

        SharedChatService.SharedChatViewDTO dto = service.getSharedChat("tok-123", owner);

        assertThat(dto.getChatId()).isEqualTo(chatId.toString());
    }

    @Test
    void getSharedChat_nonPublicLink_superAdminFromAnotherTenantIsAllowed() {
        SharedChatLink link = link(false, tenantA);
        when(linkRepository.findByToken("tok-123")).thenReturn(Optional.of(link));
        when(sessionRepository.findById(chatId)).thenReturn(Optional.of(ownerSession()));
        when(messageRepository.findByChatIdOrderByCreatedAtAsc(chatId)).thenReturn(List.of());
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());

        UserPrincipal superAdmin = new UserPrincipal(UUID.randomUUID(), "root", "SUPER_ADMIN", null, false);

        SharedChatService.SharedChatViewDTO dto = service.getSharedChat("tok-123", superAdmin);

        assertThat(dto.getChatId()).isEqualTo(chatId.toString());
    }

    @Test
    void getSharedChat_nonPublicLink_nullTenantOnLink_deniesNonOwner() {
        // Legacy/pre-migration data: link.tenantId is null. Must NOT be treated as a wildcard.
        SharedChatLink link = link(false, null);
        when(linkRepository.findByToken("tok-123")).thenReturn(Optional.of(link));

        UserPrincipal someTenantUser = new UserPrincipal(UUID.randomUUID(), "someone", "USER", tenantA, false);

        assertThatThrownBy(() -> service.getSharedChat("tok-123", someTenantUser))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getSharedChat_expiredLink_throwsRegardlessOfPublicAccess() {
        SharedChatLink link = link(true, tenantA);
        link.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(linkRepository.findByToken("tok-123")).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.getSharedChat("tok-123", null))
            .isInstanceOf(IllegalStateException.class);
    }

    // ── forkSharedChat: same enforcement as viewing ────────────────────────

    @Test
    void forkSharedChat_nonPublicLink_differentTenant_isRejected() {
        SharedChatLink link = link(false, tenantA);
        when(linkRepository.findByToken("tok-123")).thenReturn(Optional.of(link));

        UserPrincipal outsider = new UserPrincipal(UUID.randomUUID(), "outsider", "USER", tenantB, false);

        assertThatThrownBy(() -> service.forkSharedChat("tok-123", outsider))
            .isInstanceOf(AccessDeniedException.class);

        verify(sessionRepository, never()).save(any());
    }
}
