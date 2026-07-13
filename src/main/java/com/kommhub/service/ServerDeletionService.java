package com.kommhub.service;

import com.kommhub.model.db.Server;
import com.kommhub.model.db.ServerMember;
import com.kommhub.repository.ServerMemberRepository;
import com.kommhub.repository.ServerRepository;
import com.kommhub.websocket.managers.InstallationSessionsManager;
import com.kommhub.websocket.messages.WsAppMessage;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.ServerDeletedPayload;
import com.kommhub.websocket.senders.AppMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Hub-side coordinator for server deletion. The hub is the source of truth: it flags the server,
 * hides it from members and tells the owning installation to purge its data (now, or via sync-recap
 * on reconnect). The hub finalizes (hard-deletes its rows) only once the installation confirms.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServerDeletionService {

    private final ServerRepository serverRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final InstallationSessionsManager installationSessionsManager;
    private final AppMessageSender appMessageSender;

    /** Flag the server, notify members so it leaves their lists, and ask the installation to purge. */
    @Transactional
    public void requestDeletion(UUID serverId, UUID installationId, UUID requesterId) {
        Server server = serverRepository.findById(serverId).orElse(null);
        if (server == null) return;

        if (!Boolean.TRUE.equals(server.getPendingDeletion())) {
            server.setPendingDeletion(true);
            server.setDeletionRequestedAt(LocalDateTime.now());
            server.setDeletionRequestedBy(requesterId);
            serverRepository.save(server);
        }

        List<UUID> memberIds = serverMemberRepository.findByServerId(serverId).stream()
                .map(ServerMember::getUserId)
                .toList();

        WsAppMessage notice = WsAppMessage.builder()
                .type(WsMessageType.SERVER_DELETED)
                .payload(ServerDeletedPayload.builder().serverId(serverId).build())
                .build();
        memberIds.forEach(userId -> appMessageSender.sendToUser(userId, notice));

        installationSessionsManager.dispatchToInstallation(
                WsMessageType.SERVER_DELETE_NOTIFICATION, installationId,
                ServerDeletedPayload.builder().serverId(serverId).build());

        log.info("SERVER deletion requested: serverId={} installationId={} by userId={} ({} member(s) notified)",
                serverId, installationId, requesterId, memberIds.size());
    }
}
