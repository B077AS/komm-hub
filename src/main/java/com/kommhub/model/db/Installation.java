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
@Table(name = "installations")
public class Installation {

    @Id
    @UuidGenerator
    @Column(name = "installation_id", nullable = false, updatable = false, length = 36)
    private UUID installationId;

    @Column(name = "installation_name", nullable = false)
    private String installationName;

    @Column(name = "owner_id", nullable = false, length = 36)
    private UUID ownerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "installations_ibfk_1"))
    private User owner;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "port")
    private Integer port;

    @Column(name = "signal_port")
    private Integer signalPort;

    @Column(name = "tcp_port")
    private Integer tcpPort;

    @Column(name = "media_port")
    private Integer mediaPort;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InstallationStatus status = InstallationStatus.NOT_VERIFIED;

    @Column(name = "setup_token")
    private String setupToken;

    @Column(name = "csr", columnDefinition = "TEXT")
    private String csr;

    @Column(name = "certificate", columnDefinition = "TEXT")
    private String certificate;

    @Column(name = "certificate_issued_at")
    private LocalDateTime certificateIssuedAt;

    @Column(name = "certificate_revoked", nullable = false)
    @Builder.Default
    private Boolean certificateRevoked = false;

    @Column(name = "certificate_revoked_at")
    private LocalDateTime certificateRevokedAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum InstallationStatus {
        NOT_VERIFIED,  // Created but not yet claimed by JAR
        ONLINE,        // Currently connected via WebSocket
        OFFLINE        // Not connected
    }
}
