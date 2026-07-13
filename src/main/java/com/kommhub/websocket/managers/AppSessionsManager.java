package com.kommhub.websocket.managers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kommhub.model.db.User;
import com.kommhub.repository.UserRepository;
import com.kommhub.service.PresenceService;
import com.kommhub.websocket.interfaces.AppInboundMessageHandler;
import com.kommhub.websocket.messages.WsAppMessage;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.UserStatusUpdatedPayload;
import com.kommhub.websocket.senders.AppMessageSender;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppSessionsManager extends TextWebSocketHandler {

    private final Gson gson;
    private final UserRepository userRepository;
    private final ConfigurableApplicationContext applicationContext;
    private final List<AppInboundMessageHandler> inboundHandlerList;
    private final AppMessageSender appMessageSender;
    private final PresenceService presenceService;

    private Map<WsMessageType, AppInboundMessageHandler> inboundHandlers;

    @PostConstruct
    private void init() {
        inboundHandlers = inboundHandlerList.stream()
                .collect(Collectors.toMap(AppInboundMessageHandler::getType, Function.identity()));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        User principal = getUser(session);
        if (principal == null) {
            log.warn("Unauthenticated WebSocket connection rejected (session {})", session.getId());
            closeQuietly(session);
            return;
        }

        User user = userRepository.findById(principal.getUserId()).orElse(null);
        if (user == null) {
            closeQuietly(session);
            return;
        }

        // Close any existing session for this user (duplicate login from another window).
        WebSocketSession existing = appMessageSender.getSession(user.getUserId());
        if (existing != null && existing.isOpen()) {
            try { existing.close(CloseStatus.NORMAL); } catch (Exception ignored) {}
        }

        appMessageSender.register(user.getUserId(), session);

        // Restore the status the user chose for themselves (defaults to ONLINE).
        User.UserStatus restored = user.getPreferredStatus();
        if (restored == null || restored == User.UserStatus.OFFLINE) {
            restored = User.UserStatus.ONLINE;
        }
        user.setStatus(restored);
        user.setLastOnline(LocalDateTime.now());
        userRepository.save(user);

        appMessageSender.sendToUser(user.getUserId(),
                new WsAppMessage(WsMessageType.USER_STATUS_UPDATED,
                        UserStatusUpdatedPayload.builder().status(restored).build()));
        presenceService.broadcastStatusToCoMembers(user.getUserId(), restored);
        log.info("WebSocket connected: {} (session {})", user.getUsername(), session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        User principal = getUser(session);
        if (principal != null) {
            boolean wasActive = appMessageSender.unregister(principal.getUserId(), session);
            if (wasActive) {
                if (applicationContext.isActive()) {
                    userRepository.findById(principal.getUserId())
                            .ifPresent(this::markOffline);
                } else {
                    log.warn("Skipping DB update for user={} — context is shutting down", principal.getUsername());
                }
            }
            log.info("WebSocket disconnected: {} — {} (wasActive={})", principal.getUsername(), status, wasActive);
        }
    }

    @PreDestroy
    public void onShutdown() {
        log.info("Shutdown — marking {} user(s) as OFFLINE", appMessageSender.getSessions().size());
        appMessageSender.getSessions().keySet().forEach(userId ->
                userRepository.findById(userId).ifPresent(this::markOffline));
        appMessageSender.getSessions().clear();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket transport error (session {}): {}", session.getId(), exception.getMessage());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonObject root = JsonParser.parseString(message.getPayload()).getAsJsonObject();
            WsMessageType type = WsMessageType.valueOf(root.get("type").getAsString());
            JsonObject payload = root.has("payload") && !root.get("payload").isJsonNull()
                    ? root.getAsJsonObject("payload")
                    : new JsonObject();

            AppInboundMessageHandler handler = inboundHandlers.get(type);
            if (handler != null) {
                handler.handle(session, payload);
            } else {
                log.warn("Unhandled message type from client: {}", type);
            }

        } catch (IllegalArgumentException e) {
            log.warn("Unknown message type from client (session {}): {}", session.getId(), message.getPayload());
            sendError(session, "Unknown message type");
        } catch (Exception e) {
            log.error("Error handling WebSocket message (session {})", session.getId(), e);
            sendError(session, "Internal error");
        }
    }

    // Kept for backward compatibility — delegates to AppMessageSender
    public void sendToUser(UUID userId, WsAppMessage message) {
        appMessageSender.sendToUser(userId, message);
    }

    public void broadcast(WsAppMessage message) {
        appMessageSender.broadcast(message);
    }

    public boolean isOnline(UUID userId) {
        return appMessageSender.isOnline(userId);
    }

    private void markOffline(User user) {
        // Live status always drops to OFFLINE on disconnect, regardless of the
        // user's chosen status — which stays stored in preferredStatus for next login.
        user.setStatus(User.UserStatus.OFFLINE);
        user.setLastOnline(LocalDateTime.now());
        userRepository.save(user);
        presenceService.broadcastStatusToCoMembers(user.getUserId(), User.UserStatus.OFFLINE);
    }

    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            session.sendMessage(new TextMessage(gson.toJson(
                    new WsAppMessage(WsMessageType.ERROR, Map.of("message", errorMessage)))));
        } catch (IOException e) {
            log.warn("Failed to send error to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private User getUser(WebSocketSession session) {
        if (session.getPrincipal() instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.NOT_ACCEPTABLE);
        } catch (IOException ignored) {
        }
    }
}
