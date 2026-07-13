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
@Table(name = "installation_members", indexes = {
        @Index(name = "idx_im_installation_id", columnList = "installation_id"),
        @Index(name = "idx_im_user_id", columnList = "user_id")
})
public class InstallationMember {

    @Id
    @UuidGenerator
    @Column(name = "member_id", nullable = false, updatable = false, length = 36)
    private UUID memberId;

    @Column(name = "installation_id", nullable = false, length = 36)
    private UUID installationId;

    @Column(name = "user_id", nullable = false, length = 36)
    private UUID userId;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;
}
