package com.kommhub.service;

import com.kommhub.model.db.Installation;
import com.kommhub.model.db.InstallationStatusEvent;
import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Walks a window of {@link InstallationStatusEvent} rows and buckets elapsed time into
 * uptime/downtime/unknown, used identically by the nightly rollup (a full UTC day) and the
 * live "today" computation (a partial window up to now).
 */
final class InstallationUptimeCalculator {

    @Value
    @Builder
    static class WindowStats {
        long uptimeSeconds;
        long downtimeSeconds;
        long unknownSeconds;
        int outageCount;
        long longestOutageSeconds;
    }

    static WindowStats compute(Installation.InstallationStatus statusAtStart,
                                List<InstallationStatusEvent> eventsInWindow,
                                LocalDateTime from, LocalDateTime to) {
        long uptimeSeconds = 0, downtimeSeconds = 0, unknownSeconds = 0;
        int outageCount = 0;
        long longestOutageSeconds = 0;

        Installation.InstallationStatus currentStatus = statusAtStart;
        LocalDateTime cursor = from;
        LocalDateTime openOutageStart = currentStatus == Installation.InstallationStatus.OFFLINE ? from : null;

        for (InstallationStatusEvent event : eventsInWindow) {
            long seconds = Duration.between(cursor, event.getOccurredAt()).getSeconds();
            if (currentStatus == null) unknownSeconds += seconds;
            else if (currentStatus == Installation.InstallationStatus.ONLINE) uptimeSeconds += seconds;
            else if (currentStatus == Installation.InstallationStatus.OFFLINE) downtimeSeconds += seconds;

            if (event.getStatus() == Installation.InstallationStatus.OFFLINE && openOutageStart == null) {
                openOutageStart = event.getOccurredAt();
                outageCount++;
            } else if (event.getStatus() != Installation.InstallationStatus.OFFLINE && openOutageStart != null) {
                longestOutageSeconds = Math.max(longestOutageSeconds,
                        Duration.between(openOutageStart, event.getOccurredAt()).getSeconds());
                openOutageStart = null;
            }

            currentStatus = event.getStatus();
            cursor = event.getOccurredAt();
        }

        long tailSeconds = Duration.between(cursor, to).getSeconds();
        if (currentStatus == null) unknownSeconds += tailSeconds;
        else if (currentStatus == Installation.InstallationStatus.ONLINE) uptimeSeconds += tailSeconds;
        else if (currentStatus == Installation.InstallationStatus.OFFLINE) downtimeSeconds += tailSeconds;
        if (openOutageStart != null) {
            longestOutageSeconds = Math.max(longestOutageSeconds,
                    Duration.between(openOutageStart, to).getSeconds());
        }

        return WindowStats.builder()
                .uptimeSeconds(uptimeSeconds)
                .downtimeSeconds(downtimeSeconds)
                .unknownSeconds(unknownSeconds)
                .outageCount(outageCount)
                .longestOutageSeconds(longestOutageSeconds)
                .build();
    }
}
