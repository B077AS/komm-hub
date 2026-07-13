package com.kommhub.model.dto.summary;

import com.kommhub.model.db.Badge;
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
public class BadgeSummary {

    private UUID badgeId;
    private String code;
    private String name;
    private String description;
    // Ikonli literal (e.g. "mdi2s-shield-crown") — the client renders it directly
    private String icon;
    // Hex codepoint of the icon glyph (e.g. "F04C9") — used by the web dashboard
    // to render with the MDI web font; app clients can ignore it
    private String iconCodepoint;
    private String color;
    private Badge.BadgeType type;
    private int position;
    // Set when describing a badge a user holds; null in catalog/admin contexts
    private LocalDateTime awardedAt;
    // Set in admin listings only: how many users hold this badge
    private Long userCount;
    // Set in admin listings only: the badge's single redemption token (custom badges)
    private BadgeTokenSummary token;
}
