package com.kommhub.service;

import com.kommhub.model.db.Installation;
import com.kommhub.model.db.InstallationStatusEvent;
import com.kommhub.model.db.InstallationUptimeDaily;
import com.kommhub.model.dto.summary.InstallationStatusEventSummary;
import com.kommhub.model.dto.summary.InstallationUptimeDayPoint;
import com.kommhub.model.dto.summary.InstallationUptimeSummary;
import com.kommhub.repository.InstallationRepository;
import com.kommhub.repository.InstallationStatusEventRepository;
import com.kommhub.repository.InstallationUptimeDailyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/** Assembles the {@link InstallationUptimeSummary} the status-page UI reads: rolled-up history
 *  plus a live computation for "today", which is never in {@code installation_uptime_daily} yet. */
@Service
@RequiredArgsConstructor
public class InstallationUptimeQueryService {

    private static final int MIN_RANGE_DAYS = 1;
    private static final int MAX_RANGE_DAYS = 180;

    private final InstallationRepository installationRepository;
    private final InstallationStatusEventRepository statusEventRepository;
    private final InstallationUptimeDailyRepository uptimeDailyRepository;

    public InstallationUptimeSummary getSummary(UUID installationId, int requestedDays) {
        Installation installation = installationRepository.findById(installationId)
                .orElseThrow(() -> new NoSuchElementException("Installation not found"));

        int rangeDays = Math.max(MIN_RANGE_DAYS, Math.min(requestedDays, MAX_RANGE_DAYS));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate fromDay = today.minusDays(rangeDays - 1L);

        List<InstallationUptimeDayPoint> dayPoints = new ArrayList<>();
        for (InstallationUptimeDaily rolled : uptimeDailyRepository
                .findByInstallationIdAndDayGreaterThanEqualOrderByDayAsc(installationId, fromDay)) {
            dayPoints.add(toDayPoint(rolled));
        }
        dayPoints.add(computeTodayPoint(installationId, today));

        long totalUptime = dayPoints.stream().mapToLong(InstallationUptimeDayPoint::getUptimeSeconds).sum();
        long totalDowntime = dayPoints.stream().mapToLong(InstallationUptimeDayPoint::getDowntimeSeconds).sum();
        double uptimePercentage = roundTo2((totalUptime + totalDowntime) == 0
                ? 100.0
                : 100.0 * totalUptime / (totalUptime + totalDowntime));

        List<InstallationStatusEventSummary> recentEvents = statusEventRepository
                .findTop20ByInstallationIdOrderByOccurredAtDesc(installationId).stream()
                .map(this::toEventSummary)
                .collect(Collectors.toList());

        return InstallationUptimeSummary.builder()
                .installationId(installationId)
                .currentStatus(installation.getStatus())
                .lastSeenAt(installation.getLastSeenAt())
                .rangeDays(rangeDays)
                .uptimePercentage(uptimePercentage)
                .days(dayPoints)
                .recentEvents(recentEvents)
                .build();
    }

    private InstallationUptimeDayPoint computeTodayPoint(UUID installationId, LocalDate today) {
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        Installation.InstallationStatus statusAtStart = statusEventRepository
                .findLastEventBefore(installationId, dayStart)
                .map(InstallationStatusEvent::getStatus)
                .orElse(null);
        List<InstallationStatusEvent> todayEvents =
                statusEventRepository.findInRange(installationId, dayStart, now);

        InstallationUptimeCalculator.WindowStats stats =
                InstallationUptimeCalculator.compute(statusAtStart, todayEvents, dayStart, now);

        return InstallationUptimeDayPoint.builder()
                .day(today)
                .uptimeSeconds(stats.getUptimeSeconds())
                .downtimeSeconds(stats.getDowntimeSeconds())
                .unknownSeconds(stats.getUnknownSeconds())
                .uptimePercentage(percentage(stats.getUptimeSeconds(), stats.getDowntimeSeconds()))
                .outageCount(stats.getOutageCount())
                .longestOutageSeconds(stats.getLongestOutageSeconds())
                .partial(true)
                .build();
    }

    private InstallationUptimeDayPoint toDayPoint(InstallationUptimeDaily d) {
        return InstallationUptimeDayPoint.builder()
                .day(d.getDay())
                .uptimeSeconds(d.getUptimeSeconds())
                .downtimeSeconds(d.getDowntimeSeconds())
                .unknownSeconds(d.getUnknownSeconds())
                .uptimePercentage(percentage(d.getUptimeSeconds(), d.getDowntimeSeconds()))
                .outageCount(d.getOutageCount())
                .longestOutageSeconds(d.getLongestOutageSeconds())
                .partial(false)
                .build();
    }

    private InstallationStatusEventSummary toEventSummary(InstallationStatusEvent e) {
        return InstallationStatusEventSummary.builder()
                .previousStatus(e.getPreviousStatus())
                .status(e.getStatus())
                .reason(e.getReason())
                .occurredAt(e.getOccurredAt())
                .build();
    }

    private double percentage(long uptimeSeconds, long downtimeSeconds) {
        long total = uptimeSeconds + downtimeSeconds;
        return roundTo2(total == 0 ? 100.0 : 100.0 * uptimeSeconds / total);
    }

    private double roundTo2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
