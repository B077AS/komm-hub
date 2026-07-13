package com.kommhub.model.db;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A badge awarded to a user, either automatically (beta registration) or by
 * redeeming a {@link BadgeToken}. The SUPER_ADMIN badge is never materialized
 * here — it is derived from {@link User.Role} at read time.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_badges",
        indexes = @Index(name = "idx_user_badge_user", columnList = "user_id"),
        uniqueConstraints = @UniqueConstraint(name = "uq_user_badge", columnNames = {"user_id", "badge_id"}))
public class UserBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_badge_id", nullable = false, length = 36)
    private UUID userBadgeId;

    @Column(name = "user_id", nullable = false, length = 36)
    private UUID userId;

    @Column(name = "badge_id", nullable = false, length = 36)
    private UUID badgeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "badge_id", insertable = false, updatable = false)
    @JsonIgnore
    private Badge badge;

    @CreationTimestamp
    @Column(name = "awarded_at", updatable = false)
    private LocalDateTime awardedAt;
}
