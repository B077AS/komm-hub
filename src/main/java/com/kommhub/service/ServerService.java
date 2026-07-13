package com.kommhub.service;

import com.kommhub.model.db.Installation;
import com.kommhub.model.db.Server;
import com.kommhub.model.db.ServerMember;
import com.kommhub.model.db.User;
import com.kommhub.model.dto.request.ServerCreateRequest;
import com.kommhub.model.dto.request.ServerUpdateRequest;
import com.kommhub.model.dto.summary.ServerSummary;
import com.kommhub.repository.InstallationRepository;
import com.kommhub.repository.ServerMemberRepository;
import com.kommhub.repository.ServerRepository;
import com.kommhub.repository.UserRepository;
import com.kommhub.websocket.managers.InstallationSessionsManager;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.MemberLeftPayload;
import com.kommhub.websocket.messages.payloads.ServerMemberPayload;
import com.kommhub.websocket.messages.payloads.ServerPayload;
import com.kommhub.websocket.messages.payloads.SyncRecapPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerService {

    private final UserRepository userRepository;
    private final ServerRepository serverRepository;
    private final VoiceConnectedUsersService voiceConnectedUsersService;
    private final ServerMemberRepository serverMemberRepository;
    private final InstallationRepository installationRepository;
    private final InstallationSessionsManager installationSessionsManager;
    private final PermissionProxyService permissionProxyService;
    @Value("${app.upload.avatars.servers}")
    private String avatarsUploadPath;

    public ServerSummary createServer(UUID userId, ServerCreateRequest dto) throws Exception {
        String avatarPath = null;

        if (dto.getAvatarBase64() != null && dto.getAvatarContentType() != null) {
            byte[] imageBytes = Base64.getDecoder().decode(dto.getAvatarBase64());
            String filename = UUID.randomUUID() + "." + dto.getAvatarContentType();
            Path uploadDir = Paths.get(avatarsUploadPath);
            Files.createDirectories(uploadDir);
            Path filePath = uploadDir.resolve(filename);
            Files.write(filePath, imageBytes);
            avatarPath = filePath.toString();
        }

        Server server = Server.builder()
                .serverName(dto.getServerName())
                .avatarPath(avatarPath)
                .ownerId(userId)
                .installationId(dto.getInstallationId())
                .build();
        server = serverRepository.save(server);

        ServerMember member = ServerMember.builder()
                .serverId(server.getServerId())
                .userId(userId)
                .role(ServerMember.Role.OWNER)
                .displayOrder(0)
                .build();
        member = serverMemberRepository.save(member);

        User owner = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        Installation installation = installationRepository.findById(dto.getInstallationId()).orElseThrow(() -> new RuntimeException("Installation not found"));

        ServerPayload serverPayload = ServerPayload.builder()
                .serverId(server.getServerId())
                .serverName(server.getServerName())
                .description(server.getDescription())
                .ownerId(server.getOwnerId())
                .createdAt(server.getCreatedAt())
                .build();

        List<ServerPayload> serversList = new ArrayList<>();
        serversList.add(serverPayload);

        ServerMemberPayload memberPayload = ServerMemberPayload.builder()
                .serverId(member.getServerId())
                .userId(member.getUserId())
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .build();

        SyncRecapPayload syncRecapPayload = SyncRecapPayload.builder()
                .serversList(serversList)
                .membersList(List.of(memberPayload))
                .build();

        installationSessionsManager.dispatchToInstallation(
                WsMessageType.SYNC_RECAP,
                dto.getInstallationId(),
                syncRecapPayload
        );

        return buildSummary(server, installation, member, 1L, owner, 0, 0, 0, null);
    }

    public Map<UUID, ServerSummary> getUserServers(UUID userId) {
        List<Object[]> results = serverRepository.findServersWithMembershipByUserId(userId);

        // Kick off all stats requests in parallel so offline/slow installations don't serialize
        List<CompletableFuture<ServerSummary>> futures = results.stream()
                .map(result -> {
                    Server server = (Server) result[0];
                    ServerMember member = (ServerMember) result[1];
                    User owner = (User) result[2];
                    Long totalMembers = (Long) result[3];
                    Installation inst = (Installation) result[4];

                    return CompletableFuture.supplyAsync(() -> {
                        var stats = (inst != null)
                                ? voiceConnectedUsersService.fetchServerStats(inst.getInstallationId(), server.getServerId())
                                : null;

                        int activeUsers      = stats != null ? stats.getActiveUsers()              : 0;
                        int textChannels     = stats != null ? stats.getTextChannelCount()         : 0;
                        int voiceChannels    = stats != null ? stats.getVoiceChannelCount()        : 0;
                        Integer defaultWidth = stats != null ? stats.getDefaultChannelPanelWidth() : null;

                        return buildSummary(server, inst, member, totalMembers, owner, activeUsers, textChannels, voiceChannels, defaultWidth);
                    });
                })
                .collect(Collectors.toList());

        List<ServerSummary> summaries = CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList()))
                .join();

        // Group servers by installation and fetch effective permissions in one batch call per installation.
        // Skip offline installations — their permissions are irrelevant until they come back online.
        Map<UUID, List<ServerSummary>> byInstallation = summaries.stream()
                .filter(s -> s.getInstallationId() != null)
                .collect(Collectors.groupingBy(ServerSummary::getInstallationId));

        Map<UUID, ServerSummary> summaryById = summaries.stream()
                .collect(Collectors.toMap(ServerSummary::getServerId, s -> s));

        byInstallation.forEach((installationId, installationSummaries) -> {
            if (!installationSessionsManager.isServerOnline(installationId)) return;

            List<UUID> serverIds = installationSummaries.stream()
                    .map(ServerSummary::getServerId)
                    .collect(Collectors.toList());
            Map<UUID, List<String>> perms = permissionProxyService.getEffectivePermissionsBatch(installationId, userId, serverIds);
            perms.forEach((serverId, effectivePermissions) -> {
                ServerSummary summary = summaryById.get(serverId);
                if (summary != null) summary.setEffectivePermissions(effectivePermissions);
            });
        });

        return summaryById;
    }

    public void reorderServers(UUID userId, List<UUID> serverIds) {
        for (int i = 0; i < serverIds.size(); i++) {
            ServerMember member = serverMemberRepository
                    .findByUserIdAndServerId(userId, serverIds.get(i))
                    .orElseThrow(() -> new RuntimeException("Server membership not found"));

            member.setDisplayOrder(i);
            serverMemberRepository.save(member);
        }
    }

    private ServerSummary buildSummary(Server server, Installation installation,
                                       ServerMember member, Long totalMembers,
                                       User owner, int activeUsers,
                                       int textChannelCount, int voiceChannelCount,
                                       Integer defaultChannelPanelWidth) {
        String avatarBase64 = null;
        String avatarImageFormat = null;

        if (server.getAvatarPath() != null) {
            try {
                byte[] avatarBytes = Files.readAllBytes(Paths.get(server.getAvatarPath()));
                avatarBase64 = Base64.getEncoder().encodeToString(avatarBytes);

                String ext = FilenameUtils.getExtension(server.getAvatarPath());
                avatarImageFormat = ext.isEmpty() ? null : ext.toLowerCase();

            } catch (Exception e) {
                log.warn("Failed to read avatar for server {}: {}", server.getServerId(), e.getMessage());
            }
        }

        return ServerSummary.builder()
                .serverId(server.getServerId())
                .serverName(server.getServerName())
                .description(server.getDescription())
                .installationId(server.getInstallationId())
                .ipAddress(installation != null ? installation.getIpAddress() : null)
                .port(installation != null ? installation.getPort() : null)
                .signalPort(installation != null ? installation.getSignalPort() : null)
                .avatar(avatarBase64)
                .avatarImageFormat(avatarImageFormat)
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .displayOrder(member.getDisplayOrder())
                .totalMembers(totalMembers.intValue())
                .ownerId(owner.getUserId())
                .ownerUsername(owner.getUsername())
                .activeUsers(activeUsers)
                .textChannelCount(textChannelCount)
                .voiceChannelCount(voiceChannelCount)
                .status(installation.getStatus())
                .defaultChannelPanelWidth(defaultChannelPanelWidth)
                .channelNotificationsEnabled(member.isChannelNotificationsEnabled())
                .build();
    }

    public List<ServerSummary> getServersForInstallation(UUID installationId) {
        Installation installation = installationRepository.findById(installationId).orElse(null);
        List<Server> servers = serverRepository.findByInstallationId(installationId);

        return servers.stream().map(server -> {
            String avatarBase64 = null;
            String avatarImageFormat = null;
            if (server.getAvatarPath() != null) {
                try {
                    byte[] avatarBytes = Files.readAllBytes(Paths.get(server.getAvatarPath()));
                    avatarBase64 = Base64.getEncoder().encodeToString(avatarBytes);
                    String ext = FilenameUtils.getExtension(server.getAvatarPath());
                    avatarImageFormat = ext.isEmpty() ? null : ext.toLowerCase();
                } catch (Exception e) {
                    log.warn("Failed to read avatar for server {}: {}", server.getServerId(), e.getMessage());
                }
            }

            String ownerUsername = userRepository.findById(server.getOwnerId())
                    .map(User::getUsername).orElse(null);
            long totalMembers = serverMemberRepository.countByServerId(server.getServerId());

            return ServerSummary.builder()
                    .serverId(server.getServerId())
                    .serverName(server.getServerName())
                    .description(server.getDescription())
                    .installationId(installationId)
                    .avatar(avatarBase64)
                    .avatarImageFormat(avatarImageFormat)
                    .totalMembers((int) totalMembers)
                    .ownerId(server.getOwnerId())
                    .ownerUsername(ownerUsername)
                    .activeUsers(0)
                    .status(installation != null ? installation.getStatus() : Installation.InstallationStatus.OFFLINE)
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public void leaveServer(UUID userId, UUID serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NoSuchElementException("Server not found"));

        ServerMember member = serverMemberRepository.findByUserIdAndServerId(userId, serverId)
                .orElseThrow(() -> new SecurityException("You are not a member of this server"));

        if (member.getRole() == ServerMember.Role.OWNER) {
            throw new SecurityException("Server owners cannot leave. Delete the server instead.");
        }

        serverMemberRepository.delete(member);
        log.info("User {} left server {}", userId, serverId);

        if (server.getInstallationId() != null) {
            installationSessionsManager.dispatchToInstallation(
                    WsMessageType.MEMBER_LEFT,
                    server.getInstallationId(),
                    MemberLeftPayload.builder()
                            .serverId(serverId)
                            .userId(userId)
                            .build()
            );
        }
    }

    public void updateNotificationSettings(UUID userId, UUID serverId, Boolean channelNotificationsEnabled) {
        ServerMember member = serverMemberRepository.findByUserIdAndServerId(userId, serverId)
                .orElseThrow(() -> new RuntimeException("Server membership not found"));
        if (channelNotificationsEnabled != null) {
            member.setChannelNotificationsEnabled(channelNotificationsEnabled);
        }
        serverMemberRepository.save(member);
    }

    public ServerSummary updateServer(UUID userId, UUID serverId, ServerUpdateRequest dto) throws Exception {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Server not found"));

        ServerMember member = serverMemberRepository.findByUserIdAndServerId(userId, serverId)
                .orElseThrow(() -> new SecurityException("You are not a member of this server"));

        if (member.getRole() != ServerMember.Role.OWNER) {
            throw new SecurityException("Only the server owner can modify these settings");
        }

        if (dto.getServerName() != null && !dto.getServerName().trim().isEmpty()) {
            server.setServerName(dto.getServerName().trim());
        }

        if (dto.getDescription() != null) {
            server.setDescription(dto.getDescription().trim());
        }

        if (dto.getAvatarBase64() != null && dto.getAvatarContentType() != null) {
            // Delete old avatar if exists
            if (server.getAvatarPath() != null) {
                try {
                    Files.deleteIfExists(Paths.get(server.getAvatarPath()));
                } catch (Exception e) {
                    log.warn("Failed to delete old avatar for server {}: {}", serverId, e.getMessage());
                }
            }

            byte[] imageBytes = Base64.getDecoder().decode(dto.getAvatarBase64());
            String filename = UUID.randomUUID() + "." + dto.getAvatarContentType();
            Path uploadDir = Paths.get(avatarsUploadPath);
            Files.createDirectories(uploadDir);
            Path filePath = uploadDir.resolve(filename);
            Files.write(filePath, imageBytes);
            server.setAvatarPath(filePath.toString());
        }

        server = serverRepository.save(server);

        Installation installation = installationRepository.findById(server.getInstallationId())
                .orElseThrow(() -> new RuntimeException("Installation not found"));

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long totalMembers = serverMemberRepository.countByServerId(serverId);

        return buildSummary(server, installation, member, totalMembers, owner, 0, 0, 0, null);
    }

}