package com.kommhub.model.db;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "installation_access_tokens", indexes = {
        @Index(name = "idx_iat_code", columnList = "code"),
        @Index(name = "idx_iat_installation_id", columnList = "installation_id")
})
public class InstallationAccessToken {

    @Id
    @UuidGenerator
    @Column(name = "token_id", nullable = false, updatable = false, length = 36)
    private UUID tokenId;

    @Column(name = "installation_id", nullable = false, length = 36)
    private UUID installationId;

    @Column(name = "code", nullable = false, unique = true, length = 16)
    private String code;

    @Column(name = "created_by", nullable = false, length = 36)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "used")
    @Builder.Default
    private boolean used = false;

    @Column(name = "used_at")
    private LocalDateTime usedAt;
}
