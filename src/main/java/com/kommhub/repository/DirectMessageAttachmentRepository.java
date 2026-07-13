package com.kommhub.repository;

import com.kommhub.model.db.DirectMessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DirectMessageAttachmentRepository extends JpaRepository<DirectMessageAttachment, UUID> {

    List<DirectMessageAttachment> findByMessageIdIn(List<UUID> messageIds);

    List<DirectMessageAttachment> findByMessageId(UUID messageId);
}
