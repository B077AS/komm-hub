package com.kommhub.controller;

import com.kommhub.model.db.Installation;
import com.kommhub.model.db.Server;
import com.kommhub.model.dto.request.ServerCreateRequest;
import com.kommhub.model.dto.request.ServerNotificationRequest;
import com.kommhub.model.dto.request.ServerUpdateRequest;
import com.kommhub.model.dto.response.SuccessResponse;
import com.kommhub.model.dto.summary.ServerSummary;
import com.kommhub.model.dto.response.ErrorResponse;
import com.kommhub.model.permissions.Permission;
import com.kommhub.repository.InstallationRepository;
import com.kommhub.repository.ServerMemberRepository;
import com.kommhub.repository.ServerRepository;
import com.kommhub.security.JwtUtil;
import com.kommhub.security.SecurityUtil;
import com.kommhub.service.PermissionProxyService;
import com.kommhub.service.ServerDeletionService;
import com.kommhub.service.ServerService;
import com.kommhub.websocket.managers.InstallationSessionsManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/servers")
public class ServerController {

    private final JwtUtil jwtUtil;
    private final SecurityUtil securityUtil;
    private final ServerService serverService;
    private final ServerRepository serverRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final InstallationRepository installationRepository;
    private final PermissionProxyService permissionProxyService;
    private final ServerDeletionService serverDeletionService;
    private final InstallationSessionsManager installationSessionsManager;

    @PostMapping
    public ResponseEntity<?> createServer(@RequestBody ServerCreateRequest serverCreateRequest) {
        if (serverCreateRequest.getServerName() == null || serverCreateRequest.getServerName().trim().isEmpty()) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Server name is required");
        }
        String createName = serverCreateRequest.getServerName().trim();
        if (createName.length() > 100) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Server name must be 100 characters or fewer");
        }
        if (containsEmoji(createName)) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Server name must not contain emoji");
        }
        if (serverCreateRequest.getInstallationId() == null) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Installation ID is required");
        }

        try {
            UUID userId = securityUtil.getCurrentUserId();

            Installation installation = installationRepository.findById(serverCreateRequest.getInstallationId())
                    .orElse(null);
            if (installation == null) {
                return ErrorResponse.of(HttpStatus.NOT_FOUND, "Installation not found");
            }
            if (!installation.getOwnerId().equals(userId)) {
                return ErrorResponse.of(HttpStatus.FORBIDDEN, "Only the installation owner can create servers on it");
            }

            ServerSummary result = serverService.createServer(userId, serverCreateRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalArgumentException e) {
            log.error("Invalid base64 avatar data: {}", e.getMessage());
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Invalid avatar image data");
        } catch (Exception e) {
            log.error("Failed to create server: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create server: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> getUserServers() {
        try {
            UUID userId = securityUtil.getCurrentUserId();
            Map<UUID, ServerSummary> result = serverService.getUserServers(userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to retrieve servers: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    @PatchMapping("/{serverId}/notifications")
    public ResponseEntity<?> updateNotificationSettings(@PathVariable UUID serverId,
                                                         @RequestBody ServerNotificationRequest request) {
        try {
            UUID userId = securityUtil.getCurrentUserId();
            serverService.updateNotificationSettings(userId, serverId, request.getChannelNotificationsEnabled());
            return ResponseEntity.ok(SuccessResponse.of("Notification settings updated"));
        } catch (RuntimeException e) {
            log.error("Failed to update notification settings: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update notification settings");
        }
    }

    @PutMapping("/reorder")
    public ResponseEntity<?> updateServerOrder(@RequestBody List<UUID> serverIds) {
        try {
            UUID userId = securityUtil.getCurrentUserId();
            serverService.reorderServers(userId, serverIds);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to reorder servers: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to reorder servers: " + e.getMessage());
        }
    }

    @PutMapping("/{serverId}")
    public ResponseEntity<?> updateServer(@PathVariable UUID serverId,
                                          @RequestBody ServerUpdateRequest request) {
        if (request.getServerName() == null || request.getServerName().trim().isEmpty()) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Server name is required");
        }
        String updateName = request.getServerName().trim();
        if (updateName.length() > 100) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Server name must be 100 characters or fewer");
        }
        if (containsEmoji(updateName)) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Server name must not contain emoji");
        }
        if (request.getDescription() != null && request.getDescription().length() > 500) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Description must be 500 characters or fewer");
        }

        try {
            UUID userId = securityUtil.getCurrentUserId();
            serverService.updateServer(userId, serverId, request);
            return ResponseEntity.ok(SuccessResponse.of("Server updated successfully"));
        } catch (SecurityException e) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Invalid request data: {}", e.getMessage());
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to update server: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update server: " + e.getMessage());
        }
    }

    @DeleteMapping("/{serverId}")
    public ResponseEntity<?> deleteServer(@PathVariable UUID serverId) {
        UUID userId = securityUtil.getCurrentUserId();
        Server server = serverRepository.findById(serverId).orElse(null);
        if (server == null || server.getInstallationId() == null) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, "Server not found");
        }
        UUID installationId = server.getInstallationId();

        if (!serverMemberRepository.existsByServerIdAndUserId(serverId, userId)) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, "Not a member of this server");
        }
        if (!installationSessionsManager.isServerOnline(installationId)) {
            return ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE, "Server is offline");
        }
        // Authoritative permission check, proxied to the installation that owns the permission data.
        if (!permissionProxyService.hasPermission(installationId, serverId, userId, Permission.DELETE_SERVER)) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, "Missing permission: DELETE_SERVER");
        }

        try {
            serverDeletionService.requestDeletion(serverId, installationId, userId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to delete server {}: {}", serverId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete server: " + e.getMessage());
        }
    }

    @DeleteMapping("/{serverId}/leave")
    public ResponseEntity<?> leaveServer(@PathVariable UUID serverId) {
        try {
            UUID userId = securityUtil.getCurrentUserId();
            serverService.leaveServer(userId, serverId);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to leave server {}: {}", serverId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to leave server: " + e.getMessage());
        }
    }

    @GetMapping("/{serverId}/status")
    public ResponseEntity<?> getServerStatus(@PathVariable UUID serverId) {
        Server server = serverRepository.findById(serverId).orElse(null);
        if (server == null) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, "Server not found");
        }
        if (server.getInstallationId() == null) {
            return ResponseEntity.ok(Installation.InstallationStatus.OFFLINE.name());
        }
        Installation installation = installationRepository.findById(server.getInstallationId()).orElse(null);
        if (installation == null) {
            return ResponseEntity.ok(Installation.InstallationStatus.OFFLINE.name());
        }
        return ResponseEntity.ok(installation.getStatus().name());
    }

    private static boolean containsEmoji(String text) {
        return text.codePoints().anyMatch(cp ->
                cp > 0xFFFF
                || Character.getType(cp) == Character.OTHER_SYMBOL
                || (cp >= 0x2300 && cp <= 0x23FF)
                || (cp >= 0x2600 && cp <= 0x27BF)
                || (cp >= 0xFE00 && cp <= 0xFE0F)
                || (cp >= 0x1F000 && cp <= 0x1FFFF));
    }

    @PostMapping("/{serverId}/ticket")
    public ResponseEntity<?> requestInstallationTicket(@PathVariable UUID serverId) {
        UUID userId = securityUtil.getCurrentUserId();
        log.debug("Ticket request: serverId={}, userId={}", serverId, userId);

        boolean isMember = serverMemberRepository.existsByServerIdAndUserId(serverId, userId);
        if (!isMember) {
            log.warn("Ticket denied: userId={} is not a member of serverId={}", userId, serverId);
            return ErrorResponse.of(HttpStatus.FORBIDDEN, "You are not a member of this server");
        }

        Server server = serverRepository.findById(serverId).orElse(null);
        if (server == null) {
            log.warn("Ticket denied: serverId={} not found", serverId);
            return ErrorResponse.of(HttpStatus.NOT_FOUND, "Server not found");
        }
        if (Boolean.TRUE.equals(server.getPendingDeletion())) {
            log.warn("Ticket denied: serverId={} is pending deletion", serverId);
            return ErrorResponse.of(HttpStatus.NOT_FOUND, "Server not found");
        }
        log.debug("Server found: serverId={}, installationId={}", serverId, server.getInstallationId());

        if (server.getInstallationId() == null) {
            log.warn("Ticket denied: serverId={} has no installationId assigned", serverId);
            return ErrorResponse.of(HttpStatus.CONFLICT, "Server is not assigned to an installation");
        }

        Installation installation = installationRepository
                .findById(server.getInstallationId())
                .orElse(null);

        if (installation == null) {
            log.warn("Ticket denied: installationId={} not found in DB", server.getInstallationId());
            return ErrorResponse.of(HttpStatus.NOT_FOUND, "Installation not found");
        }
        log.debug("Installation found: installationId={}, status={}, certRevoked={}",
                installation.getInstallationId(),
                installation.getStatus(),
                installation.getCertificateRevoked());

        if (installation.getStatus() != Installation.InstallationStatus.ONLINE) {
            log.warn("Ticket denied: installationId={} status is {} (expected ONLINE)",
                    installation.getInstallationId(), installation.getStatus());
            return ErrorResponse.of(HttpStatus.CONFLICT, "Installation is not online");
        }

        if (Boolean.TRUE.equals(installation.getCertificateRevoked())) {
            log.warn("Ticket denied: installationId={} certificate is revoked",
                    installation.getInstallationId());
            return ErrorResponse.of(HttpStatus.FORBIDDEN, "Installation certificate has been revoked");
        }

        String ticket = jwtUtil.generateInstallationTicket(userId, installation.getInstallationId(), serverId);
        log.debug("Ticket issued: userId={}, installationId={}, server={}", userId, installation.getInstallationId(), serverId);

        return SuccessResponse.of(HttpStatus.CREATED.getReasonPhrase(), ticket);
    }
}