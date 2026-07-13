package com.kommhub.websocket.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kommhub.repository.ServerMemberRepository;
import com.kommhub.websocket.interfaces.InstallationInboundMessageHandler;
import com.kommhub.websocket.managers.AppSessionsManager;
import com.kommhub.websocket.messages.WsAppMessage;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.VoiceConnectedUsersUpdatePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class VoiceConnectedUsersUpdateHandler implements InstallationInboundMessageHandler {

    private final Gson gson;
    private final ServerMemberRepository serverMemberRepository;
    private final AppSessionsManager appSessionsManager;

    @Override
    public WsMessageType getType() {
        return WsMessageType.VOCIE_CONNECTED_USERS_UPDATE;
    }

    @Override
    public void handle(WebSocketSession session, JsonObject payload) {
        VoiceConnectedUsersUpdatePayload update = gson.fromJson(payload, VoiceConnectedUsersUpdatePayload.class);

        WsAppMessage message = WsAppMessage.builder()
                .type(WsMessageType.VOCIE_CONNECTED_USERS_UPDATE)
                .payload(update)
                .build();

        serverMemberRepository.findByServerId(update.getServerId())
                .stream()
                .map(m -> m.getUserId())
                .filter(appSessionsManager::isOnline)
                .forEach(userId -> appSessionsManager.sendToUser(userId, message));
    }
}