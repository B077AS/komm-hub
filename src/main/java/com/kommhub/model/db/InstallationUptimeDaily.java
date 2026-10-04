package com.kommhub.model.db;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One row per installation per UTC calendar day, rolled up from {@link InstallationStatusEvent}
 * by {@code InstallationUptimeRollupService}. Exists purely so the status-page UI doesn't have to
 * replay the full raw event log on every read - the raw log stays authoritative.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "installation_uptime_daily", uniqueConstraints = {
        @UniqueConstraint(name = "uk_uptime_daily_installation_day", columnNames = {"installation_id", "day"})
})
public class InstallationUptimeDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "installation_id", nullable = false, length = 36)
    private UUID installationId;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(name = "uptime_seconds", nullable = false)
    private long uptimeSeconds;

    @Column(name = "downtime_seconds", nullable = false)
    private long downtimeSeconds;

    // Seconds not covered by any event that day (e.g. installation created mid-day, or hub was down
    // and never recorded a transition) - surfaced separately so it's never silently counted as "up".
    @Column(name = "unknown_seconds", nullable = false)
    private long unknownSeconds;

    @Column(name = "outage_count", nullable = false)
    private int outageCount;

    @Column(name = "longest_outage_seconds", nullable = false)
    private long longestOutageSeconds;
}
