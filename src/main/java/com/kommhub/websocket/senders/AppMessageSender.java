package com.kommhub.websocket.senders;

import com.google.gson.Gson;
import com.kommhub.websocket.messages.WsAppMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppMessageSender {

    private final Gson gson;

    private final Map<UUID, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    public void register(UUID userId, WebSocketSession session) {
        userSessions.put(userId, session);
    }

    public boolean unregister(UUID userId, WebSocketSession session) {
        return userSessions.remove(userId, session);
    }

    public WebSocketSession getSession(UUID userId) {
        return userSessions.get(userId);
    }

    public boolean isOnline(UUID userId) {
        return userSessions.containsKey(userId);
    }

    public void sendToUser(UUID userId, WsAppMessage message) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) sendTo(session, message);
    }

    public void broadcast(WsAppMessage message) {
        userSessions.values().forEach(s -> sendTo(s, message));
    }

    public Map<UUID, WebSocketSession> getSessions() {
        return userSessions;
    }

    private void sendTo(WebSocketSession session, WsAppMessage message) {
        try {
            session.sendMessage(new TextMessage(gson.toJson(message)));
        } catch (IOException e) {
            log.warn("Failed to send to session {}: {}", session.getId(), e.getMessage());
        }
    }
}
