package com.kommhub.model.db;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "email_verification_tokens", indexes = {
        @Index(name = "idx_evt_user_id", columnList = "user_id")
})
public class EmailVerificationToken {

    @Id
    @UuidGenerator
    @Column(name = "token_id", nullable = false, updatable = false, length = 36)
    private UUID tokenId;

    @Column(name = "user_id", nullable = false, unique = true, length = 36)
    private UUID userId;

    @Column(name = "code", nullable = false, length = 6)
    private String code;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_resent_at")
    private LocalDateTime lastResentAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
