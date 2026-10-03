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

/**
 * Append-only log of every {@link Installation.InstallationStatus} transition, written by
 * {@code InstallationStatusEventService}. This is the source of truth uptime/downtime is computed
 * from - {@link InstallationUptimeDaily} is just a cached rollup of this table.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "installation_status_events", indexes = {
        @Index(name = "idx_status_events_installation_time", columnList = "installation_id, occurred_at")
})
public class InstallationStatusEvent {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @Column(name = "installation_id", nullable = false, length = 36)
    private UUID installationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private Installation.InstallationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Installation.InstallationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private Reason reason;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    public enum Reason {
        CONNECTED,          // WS session established, status -> ONLINE
        VALIDATED,          // CSR validated for the first time, NOT_VERIFIED -> OFFLINE
        WS_CLOSED,          // afterConnectionClosed fired (graceful close or TCP reset)
        HEARTBEAT_TIMEOUT,  // ping/pong sweep found the session stale and dropped it
        HUB_SHUTDOWN        // hub process is shutting down, can't tell if the installation is still up
    }
}
