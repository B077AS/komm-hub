package com.kommhub.websocket.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kommhub.model.db.Server;
import com.kommhub.repository.InviteLinkRepository;
import com.kommhub.repository.ServerMemberRepository;
import com.kommhub.repository.ServerRepository;
import com.kommhub.websocket.interfaces.InstallationInboundMessageHandler;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.ServerDeletedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

/**
 * Installation → hub: the installation finished purging a server's data. The hub finalizes by
 * hard-deleting its server/membership/invite rows.
 *
 * <p>Finalization is done here with repositories directly (rather than calling a service that depends
 * on {@code InstallationSessionsManager}) to avoid a bean dependency cycle, mirroring how the other
 * installation-inbound handlers do their DB work.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServerDeletionCompleteHandler implements InstallationInboundMessageHandler {

    private final Gson gson;
    private final ServerRepository serverRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final InviteLinkRepository inviteLinkRepository;

    @Override
    public WsMessageType getType() { return WsMessageType.SERVER_DELETION_COMPLETE; }

    @Override
    @Transactional
    public void handle(WebSocketSession session, JsonObject payload) {
        ServerDeletedPayload p = gson.fromJson(payload, ServerDeletedPayload.class);
        if (p == null || p.getServerId() == null) return;
        UUID serverId = p.getServerId();

        Server server = serverRepository.findById(serverId).orElse(null);
        if (server == null) return; // already finalized

        // Authorization: the notifying installation must actually host this server.
        UUID installationId = (UUID) session.getAttributes().get("installationId");
        if (installationId == null || !installationId.equals(server.getInstallationId())) {
            log.warn("SERVER_DELETION_COMPLETE rejected — installationId={} does not host serverId={}",
                    installationId, serverId);
            return;
        }

        inviteLinkRepository.deleteByServerId(serverId);
        serverMemberRepository.deleteByServerId(serverId);
        serverRepository.delete(server);
        log.info("SERVER deletion finalized: serverId={}", serverId);
    }
}
