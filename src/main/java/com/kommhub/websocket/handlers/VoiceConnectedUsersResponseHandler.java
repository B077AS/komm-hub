package com.kommhub.websocket.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kommhub.websocket.VoiceConnectedUsersPendingRegistry;
import com.kommhub.websocket.interfaces.InstallationInboundMessageHandler;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.VoiceConnectedUsersResponsePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceConnectedUsersResponseHandler implements InstallationInboundMessageHandler {

    private final Gson gson;
    private final VoiceConnectedUsersPendingRegistry voiceConnectedUsersPendingRegistry;

    @Override
    public WsMessageType getType() {
        return WsMessageType.VOCIE_CONNECTED_USERS_RESPONSE;
    }

    @Override
    public void handle(WebSocketSession session, JsonObject payload) {
        VoiceConnectedUsersResponsePayload response =
                gson.fromJson(payload, VoiceConnectedUsersResponsePayload.class);

        voiceConnectedUsersPendingRegistry.complete(response.getCorrelationId(), response);
        log.debug("Received ACTIVE_USERS_RESPONSE: serverId={}, activeUsers={}, text={}, voice={}",
                response.getServerId(), response.getActiveUsers(), response.getTextChannelCount(), response.getVoiceChannelCount());
    }
}