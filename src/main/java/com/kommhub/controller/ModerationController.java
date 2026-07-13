package com.kommhub.controller;

import com.kommhub.model.db.Server;
import com.kommhub.model.db.ServerMember;
import com.kommhub.model.db.User;
import com.kommhub.model.dto.response.ErrorResponse;
import com.kommhub.model.dto.response.MemberStatusPageResponse;
import com.kommhub.model.dto.response.ServerMemberPageResponse;
import com.kommhub.model.dto.response.UserStatusDto;
import com.kommhub.model.dto.summary.ServerMemberDto;
import com.kommhub.repository.ServerMemberRepository;
import com.kommhub.repository.ServerRepository;
import com.kommhub.security.SecurityUtil;
import com.kommhub.service.HubModerationService;
import com.kommhub.websocket.managers.InstallationSessionsManager;
import com.kommhub.websocket.messages.payloads.PermProxyResponsePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/moderation")
public class ModerationController {

    private static final List<User.UserStatus> ONLINE_STATUSES = List.of(
            User.UserStatus.ONLINE, User.UserStatus.AWAY, User.UserStatus.DO_NOT_DISTURB);

    private final SecurityUtil securityUtil;
    private final ServerRepository serverRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final InstallationSessionsManager installationSessionsManager;
    private final HubModerationService hubModerationService;

    @GetMapping("/{serverId}/members")
    public ResponseEntity<?> getMembers(@PathVariable UUID serverId) {
        UUID userId = securityUtil.getCurrentUserId();
        if (!isMember(userId, serverId)) return ErrorResponse.of(HttpStatus.FORBIDDEN, "Not a member of this server");

        List<Map<String, Object>> members = serverMemberRepository.findByServerId(serverId).stream()
                .map(m -> Map.<String, Object>of(
                        "userId", m.getUserId().toString(),
                        "baseRole", m.getRole().name()))
                .toList();
        return ResponseEntity.ok(members);
    }

    @GetMapping("/{serverId}/members/paged")
    public ResponseEntity<?> getMembersPaged(
            @PathVariable UUID serverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID userId = securityUtil.getCurrentUserId();
        if (!isMember(userId, serverId)) return ErrorResponse.of(HttpStatus.FORBIDDEN, "Not a member of this server");
        size = Math.min(size, 100);
        try {
            Page<ServerMember> membersPage = serverMemberRepository.findByServerIdExcluding(
                    serverId, userId, PageRequest.of(page, size));
            List<ServerMemberDto> members = membersPage.getContent().stream()
                    .map(sm -> ServerMemberDto.builder()
                            .userId(sm.getUserId())
                            .baseRole(sm.getRole().name())
                            .build())
                    .toList();
            return ResponseEntity.ok(ServerMemberPageResponse.builder()
                    .members(members)
                    .total(membersPage.getTotalElements())
                    .page(page)
                    .size(size)
                    .build());
        } catch (Exception e) {
            log.error("Failed to get paged members for server {}: {}", serverId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/{serverId}/members/online")
    public ResponseEntity<?> getOnlineMembers(@PathVariable UUID serverId) {
        UUID userId = securityUtil.getCurrentUserId();
        if (!isMember(userId, serverId)) return ErrorResponse.of(HttpStatus.FORBIDDEN, "Not a member of this server");
        try {
            List<ServerMember> onlineMembers = serverMemberRepository.findOnlineByServerId(
                    serverId, ONLINE_STATUSES);
            List<UserStatusDto> dtos = onlineMembers.stream()
                    .map(sm -> UserStatusDto.builder()
                            .userId(sm.getUserId())
                            .status(sm.getUser().getStatus())
                            .build())
                    .toList();
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            log.error("Failed to get online members for server {}: {}", serverId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/{serverId}/members/offline")
    public ResponseEntity<?> getOfflineMembers(
            @PathVariable UUID serverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID userId = securityUtil.getCurrentUserId();
        if (!isMember(userId, serverId)) return ErrorResponse.of(HttpStatus.FORBIDDEN, "Not a member of this server");
        size = Math.min(size, 100);
        try {
            Page<ServerMember> offlinePage = serverMemberRepository.findOfflineByServerId(
                    serverId, ONLINE_STATUSES, PageRequest.of(page, size));
            List<UserStatusDto> members = offlinePage.getContent().stream()
                    .map(sm -> UserStatusDto.builder()
                            .userId(sm.getUserId())
                            .status(sm.getUser().getStatus())
                            .build())
                    .toList();
            return ResponseEntity.ok(MemberStatusPageResponse.builder()
                    .members(members)
                    .total(offlinePage.getTotalElements())
                    .page(page)
                    .size(size)
                    .build());
        } catch (Exception e) {
            log.error("Failed to get offline members for server {}: {}", serverId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/{serverId}/members/search")
    public ResponseEntity<?> searchMembers(@PathVariable UUID serverId, @RequestParam String q) {
        UUID userId = securityUtil.getCurrentUserId();
        if (!isMember(userId, serverId)) return ErrorResponse.of(HttpStatus.FORBIDDEN, "Not a member");
        if (q == null || q.isBlank()) return ResponseEntity.ok(List.of());
        try {
            List<ServerMember> members = serverMemberRepository.searchByUsername(
                    serverId, q.trim(), PageRequest.of(0, 50));
            List<UserStatusDto> dtos = members.stream()
                    .map(sm -> UserStatusDto.builder()
                            .userId(sm.getUserId())
                            .status(sm.getUser().getStatus())
                            .build())
                    .toList();
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            log.error("Failed to search members for server {}: {}", serverId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/{serverId}/bans")
    public ResponseEntity<?> getBans(@PathVariable UUID serverId) {
        UUID userId = securityUtil.getCurrentUserId();
        UUID installationId = resolveInstallationId(serverId);
        if (installationId == null) return ErrorResponse.of(HttpStatus.NOT_FOUND, "Server not found");
        if (!isMember(userId, serverId)) return ErrorResponse.of(HttpStatus.FORBIDDEN, "Not a member of this server");
        if (!isOnline(installationId)) return ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE, "Server is offline");

        PermProxyResponsePayload result = hubModerationService.getBannedUsers(installationId, serverId, userId);
        if (!result.isSuccess()) return ErrorResponse.of(HttpStatus.BAD_GATEWAY, result.getError());
        return jsonData(result, HttpStatus.OK);
    }

    @PostMapping("/{serverId}/bans")
    public ResponseEntity<?> ban(@PathVariable UUID serverId, @RequestBody Map<String, Object> body) {
        UUID userId = securityUtil.getCurrentUserId();
        UUID installationId = resolveInstallationId(serverId);
        if (installationId == null) return ErrorResponse.of(HttpStatus.NOT_FOUND, "Server not found");
        if (!isMember(userId, serverId)) return ErrorResponse.of(HttpStatus.FORBIDDEN, "Not a member of this server");

        String targetUserIdStr = (String) body.get("userId");
        if (targetUserIdStr == null) return ErrorResponse.of(HttpStatus.BAD_REQUEST, "userId is required");
        UUID targetUserId;
        try {
            targetUserId = UUID.fromString(targetUserIdStr);
        } catch (IllegalArgumentException e) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Invalid userId");
        }
        String reason = (String) body.get("reason");

        try {
            hubModerationService.banUser(serverId, installationId, userId, targetUserId, reason);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to ban user: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @DeleteMapping("/{serverId}/bans/{targetUserId}")
    public ResponseEntity<?> unban(@PathVariable UUID serverId, @PathVariable UUID targetUserId) {
        UUID userId = securityUtil.getCurrentUserId();
        UUID installationId = resolveInstallationId(serverId);
        if (installationId == null) return ErrorResponse.of(HttpStatus.NOT_FOUND, "Server not found");
        if (!isMember(userId, serverId)) return ErrorResponse.of(HttpStatus.FORBIDDEN, "Not a member of this server");

        try {
            hubModerationService.unbanUser(serverId, installationId, targetUserId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to unban user: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/{serverId}/kicks/{targetUserId}")
    public ResponseEntity<?> kick(@PathVariable UUID serverId, @PathVariable UUID targetUserId) {
        UUID userId = securityUtil.getCurrentUserId();
        UUID installationId = resolveInstallationId(serverId);
        if (installationId == null) return ErrorResponse.of(HttpStatus.NOT_FOUND, "Server not found");
        if (!isMember(userId, serverId)) return ErrorResponse.of(HttpStatus.FORBIDDEN, "Not a member of this server");

        try {
            hubModerationService.kickUser(serverId, installationId, userId, targetUserId);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to kick user: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
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

    private ResponseEntity<byte[]> jsonData(PermProxyResponsePayload result, HttpStatus status) {
        byte[] body = (result.getData() != null ? result.getData().toString() : "[]")
                .getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
