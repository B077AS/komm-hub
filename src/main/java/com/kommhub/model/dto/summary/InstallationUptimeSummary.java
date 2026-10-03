package com.kommhub.model.dto.summary;

import com.kommhub.model.db.Installation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Top-level response for the installation status-page endpoint. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstallationUptimeSummary {
    private UUID installationId;
    private Installation.InstallationStatus currentStatus;
    private LocalDateTime lastSeenAt;
    private int rangeDays;
    private double uptimePercentage;
    private List<InstallationUptimeDayPoint> days;
    private List<InstallationStatusEventSummary> recentEvents;
}
