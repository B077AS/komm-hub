package com.kommhub.model.db;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(DmConversationHidden.PK.class)
@Table(name = "dm_conversation_hidden", indexes = {
        @Index(name = "idx_dm_hidden_user", columnList = "user_id")
})
public class DmConversationHidden {

    @Id
    @Column(name = "user_id", nullable = false, length = 36)
    private UUID userId;

    @Id
    @Column(name = "other_user_id", nullable = false, length = 36)
    private UUID otherUserId;

    @Column(name = "hidden_before", nullable = false)
    private LocalDateTime hiddenBefore;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private UUID userId;
        private UUID otherUserId;
    }
}
