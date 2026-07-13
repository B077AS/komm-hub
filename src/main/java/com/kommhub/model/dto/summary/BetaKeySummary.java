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
public class BetaKeySummary {

    private UUID betaKeyId;
    private String keyValue;
    private boolean used;
    private String usedByUsername;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
