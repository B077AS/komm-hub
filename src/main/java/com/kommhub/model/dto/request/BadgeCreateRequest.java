package com.kommhub.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BadgeCreateRequest {

    private String name;
    private String description;
    // Ikonli materialdesign2 literal, e.g. "mdi2t-trophy"
    private String icon;
    // #RRGGBB
    private String color;

    // Limits for the badge's first redemption token, generated together with
    // the badge. null/<= 0 maxUses = unlimited; null expiresAt = never expires.
    private Integer maxUses;
    private LocalDateTime expiresAt;
}
