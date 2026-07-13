package com.kommhub.model.db;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "friends",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"requester_id", "addressee_id"})
        }
)
public class Friend {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "friend_id", nullable = false, length = 36)
    private UUID friendId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "addressee_id", nullable = false)
    private User addressee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private FriendStatus status = FriendStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum FriendStatus {
        PENDING,    // Request sent, awaiting response
        ACCEPTED,   // Both users are friends
        DECLINED,   // Addressee declined the request
        BLOCKED     // One user has blocked the other
    }
}