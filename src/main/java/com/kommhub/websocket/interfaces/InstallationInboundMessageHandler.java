package com.kommhub.websocket.interfaces;

import com.google.gson.JsonObject;
import com.kommhub.websocket.messages.WsMessageType;
import org.springframework.web.socket.WebSocketSession;

public interface InstallationInboundMessageHandler {
    WsMessageType getType();
    void handle(WebSocketSession session, JsonObject payload);
}