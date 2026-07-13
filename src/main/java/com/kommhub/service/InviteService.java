package com.kommhub.service;

import com.kommhub.model.db.*;
import com.kommhub.model.dto.request.CreateInviteRequest;
import com.kommhub.model.dto.response.InviteLinkResponse;
import com.kommhub.model.dto.response.InviteSummary;
import com.kommhub.model.permissions.Permission;
import com.kommhub.repository.*;
import com.kommhub.websocket.managers.InstallationSessionsManager;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.MemberJoinedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InviteService {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InviteLinkRepository inviteLinkRepository;
    private final ServerRepository serverRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final UserRepository userRepository;
    private final InstallationRepository installationRepository;
    private final InstallationSessionsManager installationSessionsManager;
    private final PermissionProxyService permissionProxyService;

    public InviteLinkResponse createInvite(UUID userId, CreateInviteRequest req) {
        Server server = serverRepository.findById(req.getServerId())
                .orElseThrow(() -> new NoSuchElementException("Server not found"));

        serverMemberRepository.findByUserIdAndServerId(userId, req.getServerId())
                .orElseThrow(() -> new SecurityException("You are not a member of this server"));

        UUID installationId = server.getInstallationId();
        if (installationId == null || !permissionProxyService.hasPermission(installationId, server.getServerId(), userId, Permission.INVITE_USERS)) {
            throw new SecurityException("You don't have permission to create invite links");
        }

        String code = generateUniqueCode();
        LocalDateTime expiresAt = req.getExpiresInHours() != null
                ? LocalDateTime.now().plusHours(req.getExpiresInHours())
                : null;

        InviteLink invite = InviteLink.builder()
                .code(code)
                .serverId(server.getServerId())
                .creatorId(userId)
                .expiresAt(expiresAt)
                .build();
        invite = inviteLinkRepository.save(invite);

        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return buildResponse(invite, server, creator.getUsername());
    }

    public InviteLinkResponse getInviteInfo(String code) {
        InviteLink invite = inviteLinkRepository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("Invite link not found"));

        if (!invite.isActive()) {
            throw new IllegalStateException("This invite link has been deactivated");
        }
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("This invite link has expired");
        }

        Server server = serverRepository.findById(invite.getServerId())
                .orElseThrow(() -> new NoSuchElementException("Server not found"));

        User creator = userRepository.findById(invite.getCreatorId())
                .orElse(null);

        return buildResponse(invite, server, creator != null ? creator.getUsername() : "Unknown");
    }

    @Transactional
    public String useInvite(UUID userId, String code) {
        InviteLink invite = inviteLinkRepository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("Invite link not found"));

        if (!invite.isActive()) {
            throw new IllegalStateException("This invite link has been deactivated");
        }
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("This invite link has expired");
        }

        Server server = serverRepository.findById(invite.getServerId())
                .orElseThrow(() -> new NoSuchElementException("Server not found"));

        if (serverMemberRepository.existsByServerIdAndUserId(server.getServerId(), userId)) {
            throw new IllegalArgumentException("You are already a member of this server");
        }

        int nextOrder = serverMemberRepository.findMaxDisplayOrderByUserId(userId)
                .map(max -> max + 1)
                .orElse(0);

        ServerMember newMember = ServerMember.builder()
                .serverId(server.getServerId())
                .userId(userId)
                .role(ServerMember.Role.MEMBER)
                .displayOrder(nextOrder)
                .build();
        newMember = serverMemberRepository.save(newMember);

        invite.setUses(invite.getUses() + 1);
        inviteLinkRepository.save(invite);

        User user = userRepository.findById(userId).orElse(null);

        if (server.getInstallationId() != null) {
            MemberJoinedPayload payload = MemberJoinedPayload.builder()
                    .serverId(server.getServerId())
                    .userId(userId)
                    .username(user != null ? user.getUsername() : "Unknown")
                    .joinedAt(newMember.getJoinedAt())
                    .role(ServerMember.Role.MEMBER)
                    .build();

            Installation installation = installationRepository.findById(server.getInstallationId()).orElse(null);
            if (installation != null) {
                installationSessionsManager.dispatchToInstallation(
                        WsMessageType.MEMBER_JOINED,
                        installation.getInstallationId(),
                        payload
                );
            }
        }

        log.info("User {} joined server {} via invite code {}", userId, server.getServerId(), code);
        return server.getServerName();
    }

    public List<InviteSummary> listServerInvites(UUID userId, UUID serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NoSuchElementException("Server not found"));

        serverMemberRepository.findByUserIdAndServerId(userId, serverId)
                .orElseThrow(() -> new SecurityException("You are not a member of this server"));

        UUID installationId = server.getInstallationId();
        if (installationId == null || !permissionProxyService.hasPermission(installationId, serverId, userId, Permission.DELETE_INVITES)) {
            throw new SecurityException("You don't have permission to manage invite links");
        }

        LocalDateTime now = LocalDateTime.now();
        List<InviteLink> active = inviteLinkRepository.findByServerIdAndActiveTrue(serverId).stream()
                .filter(i -> i.getExpiresAt() == null || i.getExpiresAt().isAfter(now))
                .toList();

        Map<UUID, String> usernamesById = userRepository.findAllById(
                        active.stream().map(InviteLink::getCreatorId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getUserId, User::getUsername));

        return active.stream()
                .sorted(Comparator.comparing(InviteLink::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(i -> InviteSummary.builder()
                        .inviteLinkId(i.getInviteLinkId())
                        .code(i.getCode())
                        .creatorId(i.getCreatorId())
                        .creatorUsername(usernamesById.getOrDefault(i.getCreatorId(), "Unknown"))
                        .createdAt(i.getCreatedAt())
                        .expiresAt(i.getExpiresAt())
                        .uses(i.getUses())
                        .build())
                .collect(Collectors.toList());
    }

    public void deleteInvite(UUID userId, UUID inviteLinkId) {
        InviteLink invite = inviteLinkRepository.findById(inviteLinkId)
                .orElseThrow(() -> new NoSuchElementException("Invite link not found"));

        Server server = serverRepository.findById(invite.getServerId())
                .orElseThrow(() -> new NoSuchElementException("Server not found"));

        UUID installationId = server.getInstallationId();
        if (installationId == null || !permissionProxyService.hasPermission(installationId, server.getServerId(), userId, Permission.DELETE_INVITES)) {
            throw new SecurityException("You don't have permission to delete invite links");
        }

        inviteLinkRepository.delete(invite);
        log.info("User {} deleted invite link {} on server {}", userId, inviteLinkId, server.getServerId());
    }

    private InviteLinkResponse buildResponse(InviteLink invite, Server server, String creatorUsername) {
        String avatarBase64 = null;
        String avatarImageFormat = null;

        if (server.getAvatarPath() != null) {
            try {
                byte[] avatarBytes = Files.readAllBytes(Paths.get(server.getAvatarPath()));
                avatarBase64 = Base64.getEncoder().encodeToString(avatarBytes);
                String ext = FilenameUtils.getExtension(server.getAvatarPath());
                avatarImageFormat = ext.isEmpty() ? null : ext.toLowerCase();
            } catch (Exception e) {
                log.warn("Failed to read avatar for server {}: {}", server.getServerId(), e.getMessage());
            }
        }

        return InviteLinkResponse.builder()
                .inviteLinkId(invite.getInviteLinkId())
                .code(invite.getCode())
                .serverId(server.getServerId())
                .serverName(server.getServerName())
                .serverAvatarBase64(avatarBase64)
                .serverAvatarImageFormat(avatarImageFormat)
                .creatorId(invite.getCreatorId())
                .creatorUsername(creatorUsername)
                .createdAt(invite.getCreatedAt())
                .expiresAt(invite.getExpiresAt())
                .uses(invite.getUses())
                .active(invite.isActive())
                .build();
    }

    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            code = sb.toString();
        } while (inviteLinkRepository.findByCode(code).isPresent());
        return code;
    }
}
