package com.kommhub.model.db;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "servers")
public class Server {

    @Id
    @UuidGenerator
    @Column(name = "server_id", nullable = false, updatable = false, length = 36)
    private UUID serverId;

    @Column(name = "server_name", nullable = false, length = 100)
    private String serverName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "installation_id", length = 36)
    private UUID installationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installation_id", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "servers_ibfk_installation"))
    private Installation installation;

    @Column(name = "avatar_path")
    private String avatarPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility")
    @Builder.Default
    private Visibility visibility = Visibility.INVITE_ONLY;

    @Column(name = "owner_id", nullable = false, length = 36)
    private UUID ownerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "servers_ibfk_1"))
    private User owner;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Source of truth for deletion. When true the server is hidden from members' lists and the
     * owning installation is told (now or via sync-recap on reconnect) to purge its data. The row is
     * hard-deleted only once the installation confirms the purge completed.
     */
    @Column(name = "pending_deletion")
    @Builder.Default
    private Boolean pendingDeletion = false;

    @Column(name = "deletion_requested_at")
    private LocalDateTime deletionRequestedAt;

    @Column(name = "deletion_requested_by", length = 36)
    private UUID deletionRequestedBy;

    public enum Visibility {
        PUBLIC,
        PRIVATE,
        INVITE_ONLY
    }
}