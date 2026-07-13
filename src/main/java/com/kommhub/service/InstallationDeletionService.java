package com.kommhub.service;

import com.kommhub.model.db.Server;
import com.kommhub.repository.InstallationRepository;
import com.kommhub.repository.InviteLinkRepository;
import com.kommhub.repository.ServerMemberRepository;
import com.kommhub.repository.ServerRepository;
import com.kommhub.websocket.managers.InstallationSessionsManager;
import com.kommhub.websocket.messages.WsAppMessage;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.InstallationDeletedPayload;
import com.kommhub.websocket.senders.AppMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstallationDeletionService {

    private final InstallationRepository installationRepository;
    private final ServerRepository serverRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final InviteLinkRepository inviteLinkRepository;
    private final InstallationSessionsManager installationSessionsManager;
    private final AppMessageSender appMessageSender;

    @Transactional
    public void deleteInstallation(UUID installationId, UUID requesterId) {
        var installation = installationRepository.findById(installationId)
                .orElseThrow(() -> new IllegalArgumentException("Installation not found"));

        if (!installation.getOwnerId().equals(requesterId)) {
            throw new SecurityException("Only the installation owner can delete it");
        }

        List<Server> servers = serverRepository.findByInstallationId(installationId);

        // Collect user IDs via a projection query — avoids loading ServerMember entities into the
        // session, which would cause a TransientPropertyValueException when the Server is deleted
        // in the same flush cycle.
        Set<UUID> affectedUserIds = servers.stream()
                .flatMap(s -> serverMemberRepository.findUserIdsByServerId(s.getServerId()).stream())
                .collect(Collectors.toSet());

        // Cascade-delete hub-side data for each server (bulk JPQL — no entity tracking)
        for (Server server : servers) {
            inviteLinkRepository.deleteByServerId(server.getServerId());
            serverMemberRepository.deleteByServerId(server.getServerId());
            serverRepository.delete(server);
        }

        // Force-disconnect before deleting the row so afterConnectionClosed doesn't race with us
        installationSessionsManager.forceDisconnect(installationId);

        installationRepository.delete(installation);

        log.info("Installation deleted: installationId={}, requestedBy={}, serversRemoved={}, membersNotified={}",
                installationId, requesterId, servers.size(), affectedUserIds.size());

        // Send the push notification only after the transaction successfully commits.
        // If we sent it inside the transaction and the commit later failed, clients would remove
        // the installation from their UI even though nothing was actually deleted.
        WsAppMessage notice = WsAppMessage.builder()
                .type(WsMessageType.INSTALLATION_DELETED)
                .payload(InstallationDeletedPayload.builder().installationId(installationId).build())
                .build();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                affectedUserIds.forEach(userId -> appMessageSender.sendToUser(userId, notice));
            }
        });
    }
}
