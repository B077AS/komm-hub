package com.kommhub.websocket.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kommhub.websocket.ModerationProxyPendingRegistry;
import com.kommhub.websocket.interfaces.InstallationInboundMessageHandler;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.PermProxyResponsePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class GetBansResponseHandler implements InstallationInboundMessageHandler {

    private final Gson gson;
    private final ModerationProxyPendingRegistry registry;

    @Override
    public WsMessageType getType() { return WsMessageType.GET_BANS_RESPONSE; }

    @Override
    public void handle(WebSocketSession session, JsonObject payload) {
        PermProxyResponsePayload response = gson.fromJson(payload, PermProxyResponsePayload.class);
        registry.complete(response.getCorrelationId(), response);
    }
}
