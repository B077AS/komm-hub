package com.kommhub.websocket.managers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kommhub.model.db.Installation;
import com.kommhub.repository.InstallationRepository;
import com.kommhub.service.InstallationService;
import com.kommhub.websocket.interfaces.InstallationInboundMessageHandler;
import com.kommhub.websocket.messages.WsAppMessage;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.senders.InstallationMessageSender;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstallationSessionsManager extends TextWebSocketHandler {

    private final Gson gson;
    private final InstallationRepository installationRepository;
    private final InstallationService installationService;
    private final ConfigurableApplicationContext applicationContext;
    private final InstallationMessageSender messageSender;
    private final List<InstallationInboundMessageHandler> inboundHandlerList;

    private Map<WsMessageType, InstallationInboundMessageHandler> inboundHandlers;

    private final Map<UUID, WebSocketSession> installationSessions = new ConcurrentHashMap<>();

    private static final String ATTR_LAST_PONG = "lastPong";

    @Value("${app.ws.heartbeat-timeout-ms:90000}")
    private long heartbeatTimeoutMs;

    @PostConstruct
    private void init() {
        inboundHandlers = inboundHandlerList.stream()
                .collect(Collectors.toMap(InstallationInboundMessageHandler::getType, Function.identity()));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID installationId = (UUID) session.getAttributes().get("installationId");
        if (installationId == null) {
            closeQuietly(session, CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        String ipAddress = (String) session.getAttributes().get("clientIpAddress");
        session.getAttributes().put(ATTR_LAST_PONG, System.currentTimeMillis());
        installationSessions.put(installationId, session);

        Boolean tlsEnabled = (Boolean) session.getAttributes().get("tlsEnabled");
        installationRepository.findById(installationId).ifPresent(inst -> {
            inst.setStatus(Installation.InstallationStatus.ONLINE);
            inst.setLastSeenAt(LocalDateTime.now());
            inst.setIpAddress(installationService.resolveEffectiveIp(ipAddress));
            inst.setTlsEnabled(Boolean.TRUE.equals(tlsEnabled));
            installationRepository.save(inst);
        });

        messageSender.sendSyncRecap(session, installationId);

        log.info("Server connected: installationId={}", installationId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        installationSessions.entrySet().removeIf(entry -> {
            if (!entry.getValue().getId().equals(session.getId())) return false;
            if (applicationContext.isActive()) markOffline(entry.getKey());
            log.info("Server disconnected: installationId={}, closeStatus={}", entry.getKey(), status);
            return true;
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonObject root = JsonParser.parseString(message.getPayload()).getAsJsonObject();
            WsMessageType type = WsMessageType.valueOf(
                    root.get("type").getAsString()
            );
            JsonObject payload = root.has("payload") && !root.get("payload").isJsonNull()
                    ? root.getAsJsonObject("payload")
                    : new JsonObject();

            InstallationInboundMessageHandler handler = inboundHandlers.get(type);
            if (handler != null) {
                handler.handle(session, payload);
            } else {
                log.warn("Unhandled inbound message type from installation: {}", type);
            }

        } catch (IllegalArgumentException e) {
            log.warn("Unknown message type from installation (session {}): {}", session.getId(), message.getPayload());
        } catch (Exception e) {
            log.error("Error handling installation message (session {})", session.getId(), e);
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        session.getAttributes().put(ATTR_LAST_PONG, System.currentTimeMillis());
    }

    /**
     * Liveness sweep — same rationale as {@link AppSessionsManager#heartbeatSweep()}:
     * an installation that dies without a TCP close would otherwise stay ONLINE forever.
     */
    @Scheduled(fixedDelayString = "${app.ws.heartbeat-interval-ms:30000}")
    public void heartbeatSweep() {
        long now = System.currentTimeMillis();
        installationSessions.forEach((installationId, session) -> {
            Long lastPong = (Long) session.getAttributes().get(ATTR_LAST_PONG);
            boolean stale = lastPong == null || now - lastPong > heartbeatTimeoutMs;
            if (!session.isOpen() || stale) {
                log.warn("Dropping dead installation session {} ({})",
                        installationId, stale ? "heartbeat timeout" : "already closed");
                dropSession(installationId, session);
                return;
            }
            try {
                session.sendMessage(new PingMessage());
            } catch (IOException e) {
                log.warn("Ping failed for installation {}: {}", installationId, e.getMessage());
                dropSession(installationId, session);
            } catch (Exception e) {
                // e.g. a concurrent text send in progress — connection is alive, retry next sweep
                log.debug("Ping skipped for installation {}: {}", installationId, e.getMessage());
            }
        });
    }

    private void dropSession(UUID installationId, WebSocketSession session) {
        if (installationSessions.remove(installationId, session)) {
            markOffline(installationId);
        }
        closeQuietly(session, CloseStatus.GOING_AWAY);
    }

    @PreDestroy
    public void onShutdown() {
        log.info("Shutdown detected — marking {} connected installation(s) as OFFLINE", installationSessions.size());
        installationSessions.keySet().forEach(this::markOffline);
        installationSessions.clear();
    }

    public void dispatchToInstallation(WsMessageType type, UUID installationId, Object payload) {
        WebSocketSession session = installationSessions.get(installationId);
        if (session == null || !session.isOpen()) {
            log.warn("Cannot dispatch to installationId={} — session not found or closed", installationId);
            return;
        }
        try {
            WsAppMessage message = WsAppMessage.builder()
                    .type(type)
                    .payload(payload)
                    .build();
            String json = gson.toJson(message);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Failed to dispatch {} to installationId={}", type, installationId, e);
        }
    }

    public boolean isServerOnline(UUID installationId) {
        WebSocketSession session = installationSessions.get(installationId);
        return session != null && session.isOpen();
    }

    /**
     * Forcibly closes the installation's WebSocket session without marking it OFFLINE in the DB.
     * Used during installation deletion so the afterConnectionClosed callback doesn't try to update
     * a row that is about to be deleted.
     */
    public void forceDisconnect(UUID installationId) {
        WebSocketSession session = installationSessions.remove(installationId);
        if (session != null) closeQuietly(session, CloseStatus.GOING_AWAY);
    }

    private void markOffline(UUID installationId) {
        installationRepository.findById(installationId).ifPresent(inst -> {
            inst.setStatus(Installation.InstallationStatus.OFFLINE);
            inst.setLastSeenAt(LocalDateTime.now());
            installationRepository.save(inst);
        });
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ignored) {
        }
    }
}