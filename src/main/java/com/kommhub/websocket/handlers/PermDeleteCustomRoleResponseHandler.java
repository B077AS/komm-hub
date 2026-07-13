package com.kommhub.websocket.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kommhub.websocket.PermissionProxyPendingRegistry;
import com.kommhub.websocket.interfaces.InstallationInboundMessageHandler;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.PermProxyResponsePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class PermDeleteCustomRoleResponseHandler implements InstallationInboundMessageHandler {

    private final Gson gson;
    private final PermissionProxyPendingRegistry registry;

    @Override
    public WsMessageType getType() { return WsMessageType.PERM_DELETE_CUSTOM_ROLE_RESPONSE; }

    @Override
    public void handle(WebSocketSession session, JsonObject payload) {
        PermProxyResponsePayload response = gson.fromJson(payload, PermProxyResponsePayload.class);
        registry.complete(response.getCorrelationId(), response);
    }
}
