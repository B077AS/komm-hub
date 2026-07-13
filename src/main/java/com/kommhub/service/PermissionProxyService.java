package com.kommhub.service;

import com.kommhub.model.permissions.Permission;
import com.kommhub.websocket.PermissionProxyPendingRegistry;
import com.kommhub.websocket.managers.InstallationSessionsManager;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionProxyService {

    private final InstallationSessionsManager installationSessionsManager;
    private final PermissionProxyPendingRegistry pendingRegistry;
    private final Gson gson;

    private static final int TIMEOUT_MS = 5000;

    public PermProxyResponsePayload getServerPermissions(UUID installationId, UUID serverId, UUID userId) {
        String cid = UUID.randomUUID().toString();
        return dispatch(WsMessageType.PERM_GET_SERVER_REQUEST, installationId, cid,
                PermGetServerRequestPayload.builder()
                        .correlationId(cid).serverId(serverId).userId(userId).build());
    }

    public PermProxyResponsePayload updateRolePermission(UUID installationId, UUID serverId, UUID userId,
                                                          String role, List<String> permissions) {
        String cid = UUID.randomUUID().toString();
        return dispatch(WsMessageType.PERM_UPDATE_ROLE_REQUEST, installationId, cid,
                PermUpdateRoleRequestPayload.builder()
                        .correlationId(cid).serverId(serverId).userId(userId)
                        .role(role).permissions(permissions).build());
    }

    public PermProxyResponsePayload resetRoleToDefault(UUID installationId, UUID serverId, UUID userId, String role) {
        String cid = UUID.randomUUID().toString();
        return dispatch(WsMessageType.PERM_RESET_ROLE_REQUEST, installationId, cid,
                PermResetRoleRequestPayload.builder()
                        .correlationId(cid).serverId(serverId).userId(userId).role(role).build());
    }

    public PermProxyResponsePayload createCustomRole(UUID installationId, UUID serverId, UUID userId,
                                                      String roleName, String color, List<String> permissions) {
        String cid = UUID.randomUUID().toString();
        return dispatch(WsMessageType.PERM_CREATE_CUSTOM_ROLE_REQUEST, installationId, cid,
                PermCreateCustomRoleRequestPayload.builder()
                        .correlationId(cid).serverId(serverId).userId(userId)
                        .roleName(roleName).color(color).permissions(permissions).build());
    }

    public PermProxyResponsePayload updateCustomRole(UUID installationId, UUID serverId, UUID userId,
                                                      UUID roleId, String roleName, String color, List<String> permissions) {
        String cid = UUID.randomUUID().toString();
        return dispatch(WsMessageType.PERM_UPDATE_CUSTOM_ROLE_REQUEST, installationId, cid,
                PermUpdateCustomRoleRequestPayload.builder()
                        .correlationId(cid).serverId(serverId).userId(userId)
                        .roleId(roleId).roleName(roleName).color(color).permissions(permissions).build());
    }

    public PermProxyResponsePayload deleteCustomRole(UUID installationId, UUID serverId, UUID userId, UUID roleId) {
        String cid = UUID.randomUUID().toString();
        return dispatch(WsMessageType.PERM_DELETE_CUSTOM_ROLE_REQUEST, installationId, cid,
                PermDeleteCustomRoleRequestPayload.builder()
                        .correlationId(cid).serverId(serverId).userId(userId).roleId(roleId).build());
    }

    public Map<UUID, List<String>> getEffectivePermissionsBatch(UUID installationId, UUID userId, List<UUID> serverIds) {
        String cid = UUID.randomUUID().toString();
        PermProxyResponsePayload result = dispatch(WsMessageType.PERM_GET_EFFECTIVE_BATCH_REQUEST, installationId, cid,
                PermGetEffectiveBatchRequestPayload.builder()
                        .correlationId(cid).userId(userId).serverIds(serverIds).build());
        if (!result.isSuccess() || result.getData() == null) return Map.of();
        Map<String, List<String>> raw = gson.fromJson(result.getData(),
                new TypeToken<Map<String, List<String>>>() {}.getType());
        Map<UUID, List<String>> out = new java.util.HashMap<>();
        raw.forEach((k, v) -> out.put(UUID.fromString(k), v));
        return out;
    }

    public boolean hasPermission(UUID installationId, UUID serverId, UUID userId, Permission permission) {
        String cid = UUID.randomUUID().toString();
        PermProxyResponsePayload result = dispatch(WsMessageType.PERM_CHECK_PERMISSION_REQUEST, installationId, cid,
                PermCheckPermissionRequestPayload.builder()
                        .correlationId(cid).serverId(serverId).userId(userId).permission(permission.name()).build());
        if (!result.isSuccess() || result.getData() == null) return false;
        JsonElement data = result.getData();
        return data.isJsonPrimitive() && data.getAsBoolean();
    }

    private PermProxyResponsePayload dispatch(WsMessageType type, UUID installationId, String correlationId, Object payload) {
        var future = pendingRegistry.register(correlationId);
        installationSessionsManager.dispatchToInstallation(type, installationId, payload);
        try {
            return future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Timeout waiting for {} response: installationId={}", type, installationId);
            pendingRegistry.cancel(correlationId);
            return PermProxyResponsePayload.builder().success(false).error("Installation did not respond in time").build();
        } catch (Exception e) {
            log.error("Error waiting for {} response", type, e);
            pendingRegistry.cancel(correlationId);
            return PermProxyResponsePayload.builder().success(false).error(e.getMessage()).build();
        }
    }
}
