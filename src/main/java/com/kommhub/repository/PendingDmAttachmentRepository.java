package com.kommhub.repository;

import com.kommhub.model.db.PendingDmAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PendingDmAttachmentRepository extends JpaRepository<PendingDmAttachment, UUID> {

    List<PendingDmAttachment> findByUploadedAtBefore(LocalDateTime cutoff);
}
