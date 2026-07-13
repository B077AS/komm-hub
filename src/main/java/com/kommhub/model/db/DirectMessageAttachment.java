package com.kommhub.model.db;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "dm_attachments", indexes = {
        @Index(name = "idx_dm_attachment_message", columnList = "message_id")
})
public class DirectMessageAttachment {

    @Id
    @UuidGenerator
    @Column(name = "attachment_id", nullable = false, updatable = false)
    private UUID attachmentId;

    @Column(name = "message_id", nullable = false, length = 36)
    private UUID messageId;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_type", nullable = false)
    private String fileType;
}
