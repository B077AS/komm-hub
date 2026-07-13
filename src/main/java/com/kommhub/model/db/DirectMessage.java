package com.kommhub.model.db;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "direct_messages", indexes = {
        @Index(name = "idx_dm_sender_recipient", columnList = "sender_id, recipient_id, sent_at"),
        @Index(name = "idx_dm_recipient_sender", columnList = "recipient_id, sender_id, sent_at")
})
public class DirectMessage {

    @Id
    @UuidGenerator
    @Column(name = "message_id", nullable = false, updatable = false, length = 36)
    private UUID messageId;

    @Column(name = "sender_id", nullable = false, length = 36)
    private UUID senderId;

    @Column(name = "recipient_id", nullable = false, length = 36)
    private UUID recipientId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "has_attachments", nullable = false)
    @Builder.Default
    private Boolean hasAttachments = false;

    @Column(name = "is_edited", nullable = false)
    @Builder.Default
    private Boolean isEdited = false;

    @Column(name = "replied_to_id", length = 36)
    private UUID repliedToId;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", length = 10)
    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    @Column(name = "code_language", length = 32)
    private String codeLanguage;

    @OneToMany(mappedBy = "message", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DirectMessageReaction> reactions = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum MessageType {
        TEXT, GIF, CODE
    }
}
