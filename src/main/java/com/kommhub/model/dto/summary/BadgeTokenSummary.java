package com.kommhub.model.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BadgeTokenSummary {

    private UUID badgeTokenId;
    private String tokenValue;
    private UUID badgeId;
    private String badgeName;
    private Integer maxUses;
    private int usedCount;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private boolean expired;
    private boolean exhausted;
}
