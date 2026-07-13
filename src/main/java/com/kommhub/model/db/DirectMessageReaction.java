package com.kommhub.model.db;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "dm_reactions", indexes = {
        @Index(name = "idx_dm_reaction_message", columnList = "message_id"),
        @Index(name = "idx_dm_reaction_user", columnList = "user_id")
})
public class DirectMessageReaction {

    @EmbeddedId
    private DirectMessageReactionId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("messageId")
    @JoinColumn(name = "message_id", nullable = false, updatable = false)
    private DirectMessage message;

    @CreationTimestamp
    @Column(name = "reacted_at", updatable = false)
    private LocalDateTime reactedAt;

    @Data
    @Embeddable
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DirectMessageReactionId implements Serializable {

        @Column(name = "message_id", nullable = false, updatable = false, length = 36)
        private UUID messageId;

        @Column(name = "user_id", nullable = false, updatable = false, length = 36)
        private UUID userId;

        @Column(name = "emoji", nullable = false, updatable = false, length = 64)
        private String emoji;
    }
}
