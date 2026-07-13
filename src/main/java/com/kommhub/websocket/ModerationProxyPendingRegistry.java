package com.kommhub.websocket;

import com.kommhub.websocket.messages.payloads.PermProxyResponsePayload;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ModerationProxyPendingRegistry {

    private final Map<String, CompletableFuture<PermProxyResponsePayload>> pending = new ConcurrentHashMap<>();

    public CompletableFuture<PermProxyResponsePayload> register(String correlationId) {
        CompletableFuture<PermProxyResponsePayload> future = new CompletableFuture<>();
        pending.put(correlationId, future);
        return future;
    }

    public void complete(String correlationId, PermProxyResponsePayload response) {
        CompletableFuture<PermProxyResponsePayload> future = pending.remove(correlationId);
        if (future != null) future.complete(response);
    }

    public void cancel(String correlationId) {
        CompletableFuture<PermProxyResponsePayload> future = pending.remove(correlationId);
        if (future != null) future.cancel(true);
    }
}
