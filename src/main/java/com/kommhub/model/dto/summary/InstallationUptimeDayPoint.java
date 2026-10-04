package com.kommhub.model.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** One bar in the status-page uptime chart - a single UTC calendar day. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstallationUptimeDayPoint {
    private LocalDate day;
    private long uptimeSeconds;
    private long downtimeSeconds;
    private long unknownSeconds;
    private double uptimePercentage;
    private int outageCount;
    private long longestOutageSeconds;
    // true for "today" - still in progress, not yet rolled up, so the bar should render differently
    private boolean partial;
}
