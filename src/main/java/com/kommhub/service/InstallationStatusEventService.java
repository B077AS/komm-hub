package com.kommhub.service;

import com.kommhub.model.db.Installation;
import com.kommhub.model.db.InstallationStatusEvent;
import com.kommhub.repository.InstallationStatusEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Appends a row to {@code installation_status_events} every time an installation's status
 * actually changes. This is the single write path for the uptime/downtime log - call it from
 * wherever {@link Installation#setStatus} transitions the status, right after the new status is
 * persisted on the {@link Installation} row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstallationStatusEventService {

    private final InstallationStatusEventRepository statusEventRepository;

    public void recordTransition(UUID installationId,
                                  Installation.InstallationStatus previousStatus,
                                  Installation.InstallationStatus newStatus,
                                  InstallationStatusEvent.Reason reason) {
        if (previousStatus == newStatus) {
            // No-op transition (e.g. a heartbeat sweep racing a close that already marked it
            // offline) - skip so the event log only records real changes.
            return;
        }
        InstallationStatusEvent event = InstallationStatusEvent.builder()
                .installationId(installationId)
                .previousStatus(previousStatus)
                .status(newStatus)
                .reason(reason)
                .occurredAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
        statusEventRepository.save(event);
        log.debug("Installation status event recorded: installationId={}, {} -> {} ({})",
                installationId, previousStatus, newStatus, reason);
    }
}
