package com.kommhub.model.db;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "server_members", indexes = {@Index(name = "idx_server_member", columnList = "server_id, user_id")})
@IdClass(ServerMember.ServerMemberId.class)
public class ServerMember {

    @Id
    @Column(name = "user_id", nullable = false, length = 36)
    private UUID userId;

    @Id
    @Column(name = "server_id", nullable = false, length = 36)
    private UUID serverId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", insertable = false, updatable = false)
    private Server server;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role = Role.MEMBER;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Builder.Default
    @Column(name = "channel_notifications_enabled", nullable = false, columnDefinition = "boolean DEFAULT true")
    private boolean channelNotificationsEnabled = true;

    @Data
    public static class ServerMemberId implements Serializable {
        private UUID userId;
        private UUID serverId;
    }

    public enum Role {
        OWNER,
        ADMIN,
        MODERATOR,
        MEMBER;
    }
}