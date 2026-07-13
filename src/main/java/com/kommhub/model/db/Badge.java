package com.kommhub.model.db;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A profile badge definition. SYSTEM badges are seeded at startup and awarded
 * by hub logic (beta registration, SUPER_ADMIN role); CUSTOM badges are created
 * by a SUPER_ADMIN on the web dashboard and distributed via {@link BadgeToken}s.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "badges")
public class Badge {

    /** Code of the badge automatically awarded to closed-beta registrations. */
    public static final String CODE_BETA = "BETA";
    /** Code of the virtual badge shown for users with the SUPER_ADMIN role. */
    public static final String CODE_SUPER_ADMIN = "SUPER_ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "badge_id", nullable = false, length = 36)
    private UUID badgeId;

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Ikonli literal from the materialdesign2 pack (e.g. "mdi2s-shield-crown"),
    // rendered client-side exactly like channel icons
    @Column(name = "icon", nullable = false, length = 100)
    private String icon;

    @Column(name = "color", length = 7)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(name = "badge_type", nullable = false)
    @Builder.Default
    private BadgeType type = BadgeType.CUSTOM;

    @Column(name = "position")
    @Builder.Default
    private int position = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum BadgeType {
        SYSTEM,
        CUSTOM
    }
}
