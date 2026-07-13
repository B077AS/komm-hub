package com.kommhub.controller;

import com.kommhub.model.db.Server;
import com.kommhub.model.dto.request.CreateCustomRoleRequest;
import com.kommhub.model.dto.request.UpdateCustomRoleRequest;
import com.kommhub.model.dto.request.UpdateRolePermissionsRequest;
import com.kommhub.model.dto.response.ErrorResponse;
import com.kommhub.repository.ServerMemberRepository;
import com.kommhub.repository.ServerRepository;
import com.kommhub.security.SecurityUtil;
import com.kommhub.service.PermissionProxyService;
import com.kommhub.websocket.managers.InstallationSessionsManager;
import com.kommhub.websocket.messages.payloads.PermProxyResponsePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/permissions")
public class PermissionProxyController {

    private final SecurityUtil securityUtil;
    private final ServerRepository serverRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final InstallationSessionsManager installationSessionsManager;
    private final PermissionProxyService permissionProxyService;

    @GetMapping("/{serverId}/server")
    public ResponseEntity<?> getServerPermissions(@PathVariable UUID serverId) {
        UUID userId = securityUtil.getCurrentUserId();
        UUID installationId = resolveInstallationId(serverId);
        if (installationId == null) return ErrorResponse.of(HttpStatus.NOT_FOUND, "Server not found");
        if (!isMember(userId, serverId)) return ErrorResponse.of(HttpStatus.FORBIDDEN, "Not a member of this server");
        if (!isOnline(installationId)) return ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE, "Server is offline");

        PermProxyResponsePayload result = permissionProxyService.getServerPermissions(installationId, serverId, userId);
        if (!result.isSuccess()) return ErrorResponse.of(HttpStatus.BAD_GATEWAY, result.getError());
        return jsonData(result, HttpStatus.OK);
    }

    @PutMapping("/{serverId}/roles/{role}")
    public ResponseEntity<?> updateRolePermission(@PathVariable UUID serverId,
                                                  @PathVariable String role,
                                                  @RequestBody UpdateRolePermissionsRequest body) {
        UUID userId = securityUtil.getCurrentUserId();
        UUID installationId = resolveInstallationId(serverId);
        if (installationId == null) return ErrorResponse.of(HttpStatus.NOT_FOUND, "Server not found");
        if (!isMember(userId, serverId)) return ErrorResponse.of(HttpStatus.FORBIDDEN, "Not a member of this server");
        if (!isOnline(installationId)) return ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE, "Server is offline");

        PermProxyResponsePayload result = permissionProxyService.updateRolePermission(
                installationId, serverId, userId, role,
                body.getPermissions() != null ? body.getPermissions() : List.of());
        return toResponseEntity(result);
    }

    @PostMapping("/{serverId}/roles/{role}/reset")
    public ResponseEntity<?> resetRoleToDefault(@PathVariable UUID serverId, @PathVariable String role) {
        UUID userId = securityUtil.getCurrentUserId();
        UUID installationId = resolveInstallationId(serverId);
        if (installationId == null) return ErrorResponse.of(HttpStatus.NOT_FOUND, "Server not found");
        if (!isMember(userId, serverId)) return ErrorResponse.of(HttpStatus.FORBIDDEN, "Not a member of this server");
        if (!isOnline(installationId)) return ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE, "Server is offline");

        PermProxyResponsePayload result = permissionProxyService.resetRoleToDefault(
                installationId, serverId, userId, role);
        return toResponseEntity(result);
    }

    @PostMapping("/{serverId}/custom-roles")
    public ResponseEntity<?> createCustomRole(@PathVariable UUID serverId,
                                              @RequestBody CreateCustomRoleRequest body) {
        UUID userId = securityUtil.getCurrentUserId();
        UUID installationId = resolveInstallationId(serverId);
        if (installationId == null) return ErrorResponse.of(HttpStatus.NOT_FOUND, "Server not found");
        if (!isMember(userId, serverId)) return ErrorResponse.of(HttpStatus.FORBIDDEN, "Not a member of this server");
        if (!isOnline(installationId)) return ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE, "Server is offline");

        PermProxyResponsePayload result = permissionProxyService.createCustomRole(
                installationId, serverId, userId,
                body.getRoleName(), body.getColor(),
                body.getPermissions() != null ? body.getPermissions() : List.of());

        if (!result.isSuccess()) return ErrorResponse.of(HttpStatus.BAD_GATEWAY, result.getError());
        return jsonData(result, HttpStatus.CREATED);
    }

    @PutMapping("/{serverId}/custom-roles/{roleId}")
    public ResponseEntity<?> updateCustomRole(@PathVariable UUID serverId,
                                              @PathVariable UUID roleId,
                                              @RequestBody UpdateCustomRoleRequest body) {
        UUID userId = securityUtil.getCurrentUserId();
        UUID installationId = resolveInstallationId(serverId);
        if (installationId == null) return ErrorResponse.of(HttpStatus.NOT_FOUND, "Server not found");
        if (!isMember(userId, serverId)) return ErrorResponse.of(HttpStatus.FORBIDDEN, "Not a member of this server");
        if (!isOnline(installationId)) return ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE, "Server is offline");

        PermProxyResponsePayload result = permissionProxyService.updateCustomRole(
                installationId, serverId, userId, roleId,
                body.getRoleName(), body.getColor(),
                body.getPermissions() != null ? body.getPermissions() : List.of());

        if (!result.isSuccess()) return ErrorResponse.of(HttpStatus.BAD_GATEWAY, result.getError());
        return jsonData(result, HttpStatus.OK);
    }

    @DeleteMapping("/{serverId}/custom-roles/{roleId}")
    public ResponseEntity<?> deleteCustomRole(@PathVariable UUID serverId, @PathVariable UUID roleId) {
        UUID userId = securityUtil.getCurrentUserId();
        UUID installationId = resolveInstallationId(serverId);
        if (installationId == null) return ErrorResponse.of(HttpStatus.NOT_FOUND, "Server not found");
        if (!isMember(userId, serverId)) return ErrorResponse.of(HttpStatus.FORBIDDEN, "Not a member of this server");
        if (!isOnline(installationId)) return ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE, "Server is offline");

        PermProxyResponsePayload result = permissionProxyService.deleteCustomRole(
                installationId, serverId, userId, roleId);
        return result.isSuccess() ? ResponseEntity.noContent().build() : toResponseEntity(result);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID resolveInstallationId(UUID serverId) {
        return serverRepository.findById(serverId).map(Server::getInstallationId).orElse(null);
    }

    private boolean isMember(UUID userId, UUID serverId) {
        return serverMemberRepository.existsByServerIdAndUserId(serverId, userId);
    }

    private boolean isOnline(UUID installationId) {
        return installationSessionsManager.isServerOnline(installationId);
    }

    private ResponseEntity<?> toResponseEntity(PermProxyResponsePayload result) {
        return result.isSuccess()
                ? ResponseEntity.ok().build()
                : ErrorResponse.of(HttpStatus.BAD_GATEWAY, result.getError());
    }

    private ResponseEntity<byte[]> jsonData(PermProxyResponsePayload result, HttpStatus status) {
        byte[] body = (result.getData() != null ? result.getData().toString() : "{}")
                .getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
