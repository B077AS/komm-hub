package com.kommhub.model.db;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Server-side record of an issued refresh token, keyed by its SHA-256 hash —
 * the raw token is never stored. A token missing from this table is treated as
 * revoked (or already rotated), which is what makes logout and reuse detection
 * possible with otherwise stateless JWTs.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_rt_user_id", columnList = "user_id"),
        @Index(name = "idx_rt_token_hash", columnList = "token_hash", unique = true)
})
public class RefreshToken {

    @Id
    @UuidGenerator
    @Column(name = "token_id", nullable = false, updatable = false, length = 36)
    private UUID tokenId;

    @Column(name = "user_id", nullable = false, length = 36)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
