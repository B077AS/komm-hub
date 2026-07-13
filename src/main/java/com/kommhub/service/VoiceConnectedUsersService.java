package com.kommhub.service;

import com.kommhub.websocket.VoiceConnectedUsersPendingRegistry;
import com.kommhub.websocket.managers.InstallationSessionsManager;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.VoiceConnectedUsersRequestPayload;
import com.kommhub.websocket.messages.payloads.VoiceConnectedUsersResponsePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceConnectedUsersService {

    private final InstallationSessionsManager installationSessionsManager;
    private final VoiceConnectedUsersPendingRegistry pendingRegistry;

    private static final int TIMEOUT_MS = 2000;

    private static final VoiceConnectedUsersResponsePayload OFFLINE = new VoiceConnectedUsersResponsePayload();

    /**
     * Asks the installation for active users + channel counts for a server.
     * Returns zeroed payload if the installation is offline or doesn't respond in time.
     */
    public VoiceConnectedUsersResponsePayload fetchServerStats(UUID installationId, UUID serverId) {
        if (!installationSessionsManager.isServerOnline(installationId)) {
            return OFFLINE;
        }

        String correlationId = UUID.randomUUID().toString();
        CompletableFuture<VoiceConnectedUsersResponsePayload> future = pendingRegistry.register(correlationId);

        VoiceConnectedUsersRequestPayload requestPayload = VoiceConnectedUsersRequestPayload.builder()
                .serverId(serverId)
                .correlationId(correlationId)
                .build();

        installationSessionsManager.dispatchToInstallation(
                WsMessageType.VOCIE_CONNECTED_USERS_REQUEST,
                installationId,
                requestPayload
        );

        try {
            return future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Timeout waiting for ACTIVE_USERS_RESPONSE: installationId={}, serverId={}", installationId, serverId);
            pendingRegistry.cancel(correlationId);
            return OFFLINE;
        } catch (Exception e) {
            log.error("Error waiting for ACTIVE_USERS_RESPONSE", e);
            pendingRegistry.cancel(correlationId);
            return OFFLINE;
        }
    }
}