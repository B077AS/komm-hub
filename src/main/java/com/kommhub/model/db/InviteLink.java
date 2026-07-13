package com.kommhub.model.db;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "invite_links", indexes = {
        @Index(name = "idx_invite_link_code", columnList = "code"),
        @Index(name = "idx_invite_link_server_id", columnList = "server_id")
})
public class InviteLink {

    @Id
    @UuidGenerator
    @Column(name = "invite_link_id", nullable = false, updatable = false, length = 36)
    private UUID inviteLinkId;

    @Column(name = "code", nullable = false, unique = true, length = 16)
    private String code;

    @Column(name = "server_id", nullable = false, length = 36)
    private UUID serverId;

    @Column(name = "creator_id", nullable = false, length = 36)
    private UUID creatorId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "uses")
    @Builder.Default
    private int uses = 0;

    @Column(name = "active")
    @Builder.Default
    private boolean active = true;
}
