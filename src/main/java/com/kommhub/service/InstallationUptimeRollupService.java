package com.kommhub.service;

import com.kommhub.model.db.Installation;
import com.kommhub.model.db.InstallationStatusEvent;
import com.kommhub.model.db.InstallationUptimeDaily;
import com.kommhub.repository.InstallationRepository;
import com.kommhub.repository.InstallationStatusEventRepository;
import com.kommhub.repository.InstallationUptimeDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Turns the raw {@link InstallationStatusEvent} log into one {@link InstallationUptimeDaily} row
 * per installation per completed UTC day, so the status-page UI can read a cheap, bounded-size
 * summary instead of replaying the whole event log on every request.
 * <p>
 * Only ever rolls up days that have fully elapsed - "today" is always computed live from the raw
 * log (see {@link InstallationUptimeQueryService}), since it's still in progress.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstallationUptimeRollupService {

    private final InstallationRepository installationRepository;
    private final InstallationStatusEventRepository statusEventRepository;
    private final InstallationUptimeDailyRepository uptimeDailyRepository;

    @Scheduled(fixedDelayString = "${app.uptime.rollup-interval-ms:3600000}")
    public void rollUpAll() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (Installation installation : installationRepository.findAll()) {
            try {
                rollUpInstallation(installation.getInstallationId(), today);
            } catch (Exception e) {
                log.error("Uptime rollup failed for installation {}", installation.getInstallationId(), e);
            }
        }
    }

    @Transactional
    public void rollUpInstallation(UUID installationId, LocalDate today) {
        LocalDate earliestDay = statusEventRepository.findEarliestOccurredAt(installationId)
                .map(LocalDateTime::toLocalDate)
                .orElse(null);
        if (earliestDay == null) return; // never connected - nothing to roll up yet

        LocalDate startDay = uptimeDailyRepository.findMaxDay(installationId)
                .map(d -> d.plusDays(1))
                .orElse(earliestDay);

        for (LocalDate day = startDay; day.isBefore(today); day = day.plusDays(1)) {
            uptimeDailyRepository.save(computeDay(installationId, day));
        }
    }

    private InstallationUptimeDaily computeDay(UUID installationId, LocalDate day) {
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        Installation.InstallationStatus statusAtStart = statusEventRepository
                .findLastEventBefore(installationId, dayStart)
                .map(InstallationStatusEvent::getStatus)
                .orElse(null); // null = unknown, installation had no recorded state yet

        List<InstallationStatusEvent> dayEvents = statusEventRepository.findInRange(installationId, dayStart, dayEnd);

        InstallationUptimeCalculator.WindowStats stats =
                InstallationUptimeCalculator.compute(statusAtStart, dayEvents, dayStart, dayEnd);

        Long existingId = uptimeDailyRepository.findByInstallationIdAndDay(installationId, day)
                .map(InstallationUptimeDaily::getId)
                .orElse(null);

        return InstallationUptimeDaily.builder()
                .id(existingId)
                .installationId(installationId)
                .day(day)
                .uptimeSeconds(stats.getUptimeSeconds())
                .downtimeSeconds(stats.getDowntimeSeconds())
                .unknownSeconds(stats.getUnknownSeconds())
                .outageCount(stats.getOutageCount())
                .longestOutageSeconds(stats.getLongestOutageSeconds())
                .build();
    }
}
