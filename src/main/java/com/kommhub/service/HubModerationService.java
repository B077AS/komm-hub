package com.kommhub.service;

import com.kommhub.model.db.ServerMember;
import com.kommhub.repository.ServerMemberRepository;
import com.kommhub.websocket.ModerationProxyPendingRegistry;
import com.kommhub.websocket.managers.InstallationSessionsManager;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class HubModerationService {

    private final ServerMemberRepository serverMemberRepository;
    private final InstallationSessionsManager installationSessionsManager;
    private final ModerationProxyPendingRegistry pendingRegistry;

    private static final int TIMEOUT_MS = 5000;

    public PermProxyResponsePayload getBannedUsers(UUID installationId, UUID serverId, UUID userId) {
        String cid = UUID.randomUUID().toString();
        var future = pendingRegistry.register(cid);
        installationSessionsManager.dispatchToInstallation(WsMessageType.GET_BANS_REQUEST, installationId,
                GetBansRequestPayload.builder()
                        .correlationId(cid).serverId(serverId).userId(userId).build());
        try {
            return future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Timeout waiting for GET_BANS_RESPONSE: installationId={}", installationId);
            pendingRegistry.cancel(cid);
            return PermProxyResponsePayload.builder().success(false).error("Installation did not respond in time").build();
        } catch (Exception e) {
            log.error("Error waiting for GET_BANS_RESPONSE", e);
            pendingRegistry.cancel(cid);
            return PermProxyResponsePayload.builder().success(false).error(e.getMessage()).build();
        }
    }

    @Transactional
    public void banUser(UUID serverId, UUID installationId, UUID requesterId, UUID targetUserId, String reason) {
        if (requesterId.equals(targetUserId)) {
            throw new IllegalStateException("You cannot ban yourself");
        }

        ServerMember requesterMember = serverMemberRepository.findByUserIdAndServerId(requesterId, serverId).orElse(null);
        ServerMember targetMember = serverMemberRepository.findByUserIdAndServerId(targetUserId, serverId).orElse(null);
        if (requesterMember == null || targetMember == null) return;

        if (roleOrdinal(requesterMember.getRole()) <= roleOrdinal(targetMember.getRole())) {
            log.warn("BAN: userId={} (role={}) cannot ban userId={} (role={})",
                    requesterId, requesterMember.getRole(), targetUserId, targetMember.getRole());
            throw new IllegalStateException("Insufficient role to ban this user");
        }

        serverMemberRepository.delete(targetMember);

        if (installationSessionsManager.isServerOnline(installationId)) {
            installationSessionsManager.dispatchToInstallation(WsMessageType.BAN_USER_NOTIFICATION, installationId,
                    BanUserNotificationPayload.builder()
                            .serverId(serverId)
                            .targetUserId(targetUserId)
                            .requesterId(requesterId)
                            .reason(reason)
                            .build());
        }

        log.info("BAN: requesterId={} banned userId={} from serverId={}", requesterId, targetUserId, serverId);
    }

    @Transactional
    public void unbanUser(UUID serverId, UUID installationId, UUID targetUserId) {
        if (installationSessionsManager.isServerOnline(installationId)) {
            installationSessionsManager.dispatchToInstallation(WsMessageType.UNBAN_USER_NOTIFICATION, installationId,
                    UnbanUserNotificationPayload.builder()
                            .serverId(serverId)
                            .targetUserId(targetUserId)
                            .build());
        }

        log.info("UNBAN: userId={} unbanned from serverId={}", targetUserId, serverId);
    }

    @Transactional
    public void kickUser(UUID serverId, UUID installationId, UUID requesterId, UUID targetUserId) {
        if (requesterId.equals(targetUserId)) {
            throw new IllegalStateException("You cannot kick yourself");
        }

        ServerMember requesterMember = serverMemberRepository.findByUserIdAndServerId(requesterId, serverId).orElse(null);
        ServerMember targetMember = serverMemberRepository.findByUserIdAndServerId(targetUserId, serverId).orElse(null);
        if (requesterMember == null || targetMember == null) return;

        if (roleOrdinal(requesterMember.getRole()) <= roleOrdinal(targetMember.getRole())) {
            log.warn("KICK: userId={} (role={}) cannot kick userId={} (role={})",
                    requesterId, requesterMember.getRole(), targetUserId, targetMember.getRole());
            throw new IllegalStateException("Insufficient role to kick this user");
        }

        serverMemberRepository.delete(targetMember);

        if (installationSessionsManager.isServerOnline(installationId)) {
            installationSessionsManager.dispatchToInstallation(WsMessageType.KICK_USER_NOTIFICATION, installationId,
                    KickUserNotificationPayload.builder()
                            .serverId(serverId)
                            .targetUserId(targetUserId)
                            .requesterId(requesterId)
                            .build());
        }

        log.info("KICK: requesterId={} kicked userId={} from serverId={}", requesterId, targetUserId, serverId);
    }

    private int roleOrdinal(ServerMember.Role role) {
        return switch (role) {
            case OWNER -> 3;
            case ADMIN -> 2;
            case MODERATOR -> 1;
            case MEMBER -> 0;
        };
    }
}
