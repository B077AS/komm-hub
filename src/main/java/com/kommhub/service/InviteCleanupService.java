package com.kommhub.service;

import com.kommhub.repository.InviteLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class InviteCleanupService {

    private final InviteLinkRepository inviteLinkRepository;

    @Transactional
    @Scheduled(fixedDelayString = "${app.invites.cleanup-interval-ms:3600000}")
    public void cleanUpExpired() {
        int deleted = inviteLinkRepository.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired invite link(s)", deleted);
        }
    }
}
