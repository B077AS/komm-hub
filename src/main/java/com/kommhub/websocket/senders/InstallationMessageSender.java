package com.kommhub.websocket.senders;

import com.google.gson.Gson;
import com.kommhub.repository.ServerRepository;
import com.kommhub.websocket.messages.WsInstallationMessage;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.SyncRecapPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstallationMessageSender {

    private final Gson gson;
    private final ServerRepository serverRepository;

    public void sendSyncRecap(WebSocketSession session, UUID installationId) {
        var servers = serverRepository.findServersByInstallationId(installationId);
        var members = serverRepository.findMembersByInstallationId(installationId);
        var pendingDeletions = serverRepository.findPendingDeletionServerIdsByInstallationId(installationId);

        send(session, installationId, WsInstallationMessage.builder()
                .type(WsMessageType.SYNC_RECAP)
                .payload(SyncRecapPayload.builder()
                        .serversList(servers)
                        .membersList(members)
                        .pendingDeletionServerIds(pendingDeletions)
                        .build())
                .build());
    }

    private void send(WebSocketSession session, UUID installationId, Object message) {
        try {
            session.sendMessage(new TextMessage(gson.toJson(message)));
        } catch (IOException e) {
            log.warn("Failed to send message to installationId={}: {}", installationId, e.getMessage());
        }
    }
}
