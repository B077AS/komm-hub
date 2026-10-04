package com.kommhub.model.dto.summary;

import com.kommhub.model.db.Installation;
import com.kommhub.model.db.InstallationStatusEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One row in the "recent events" / incident log beneath the uptime chart. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstallationStatusEventSummary {
    private Installation.InstallationStatus previousStatus;
    private Installation.InstallationStatus status;
    private InstallationStatusEvent.Reason reason;
    private LocalDateTime occurredAt;
}
