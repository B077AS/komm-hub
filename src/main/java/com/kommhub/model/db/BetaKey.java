package com.kommhub.model.db;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A one-time registration key for the closed beta. When {@code komm.beta.enabled}
 * is true, /api/auth/register requires an unused key and marks it consumed by the
 * newly created account.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "beta_keys")
public class BetaKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "beta_key_id", nullable = false, length = 36)
    private UUID betaKeyId;

    @Column(name = "key_value", nullable = false, unique = true, length = 32)
    private String keyValue;

    @Column(name = "used_by_user_id")
    private UUID usedByUserId;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public boolean isUsed() {
        return usedByUserId != null;
    }
}
