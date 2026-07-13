package com.kommhub.websocket;

import com.kommhub.websocket.messages.payloads.VoiceConnectedUsersResponsePayload;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VoiceConnectedUsersPendingRegistry {

    private final Map<String, CompletableFuture<VoiceConnectedUsersResponsePayload>> pending = new ConcurrentHashMap<>();

    public CompletableFuture<VoiceConnectedUsersResponsePayload> register(String correlationId) {
        CompletableFuture<VoiceConnectedUsersResponsePayload> future = new CompletableFuture<>();
        pending.put(correlationId, future);
        return future;
    }

    public void complete(String correlationId, VoiceConnectedUsersResponsePayload payload) {
        CompletableFuture<VoiceConnectedUsersResponsePayload> future = pending.remove(correlationId);
        if (future != null) future.complete(payload);
    }

    public void cancel(String correlationId) {
        CompletableFuture<VoiceConnectedUsersResponsePayload> future = pending.remove(correlationId);
        if (future != null) future.cancel(true);
    }
}