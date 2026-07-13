package com.kommhub.model.dto.summary;

import com.kommhub.model.db.Installation;
import com.kommhub.model.db.ServerMember;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerSummary {

    private UUID serverId;
    private String serverName;
    private String description;
    private UUID installationId;
    private String ipAddress;
    private Integer port;
    private Integer signalPort;
    private String avatar;
    private String avatarImageFormat;
    private ServerMember.Role role;
    private LocalDateTime joinedAt;
    private Integer displayOrder;
    private int totalMembers;
    private UUID ownerId;
    private String ownerUsername;
    private int activeUsers;
    private int textChannelCount;
    private int voiceChannelCount;
    private Installation.InstallationStatus status;
    private Integer defaultChannelPanelWidth;
    private boolean channelNotificationsEnabled;
    private List<String> effectivePermissions;
}