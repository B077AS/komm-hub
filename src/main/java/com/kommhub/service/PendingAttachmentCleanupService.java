package com.kommhub.service;

import com.kommhub.model.db.PendingDmAttachment;
import com.kommhub.repository.PendingDmAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingAttachmentCleanupService {

    private final PendingDmAttachmentRepository pendingAttachmentRepository;

    @Value("${app.dm.attachments.pending-ttl-minutes:30}")
    private int ttlMinutes;

    @Scheduled(fixedDelayString = "${app.dm.attachments.cleanup-interval-ms:300000}")
    public void cleanUpStale() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(ttlMinutes);
        List<PendingDmAttachment> stale = pendingAttachmentRepository.findByUploadedAtBefore(cutoff);
        if (stale.isEmpty()) return;

        int deleted = 0;
        for (PendingDmAttachment pending : stale) {
            try {
                if (pending.getFilePath() != null) {
                    Files.deleteIfExists(Paths.get(pending.getFilePath()));
                }
                pendingAttachmentRepository.delete(pending);
                deleted++;
            } catch (IOException e) {
                log.warn("Failed to delete stale pending DM attachment file {}: {}", pending.getFilePath(), e.getMessage());
            }
        }
        log.info("Cleaned up {} stale pending DM attachment(s) older than {} minutes", deleted, ttlMinutes);
    }
}
