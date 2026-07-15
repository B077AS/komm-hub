package com.kommhub.service;

import com.kommhub.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Sweeps expired refresh tokens. Expired rows are already unusable — the JWT
 * expiry check rejects them before the store is consulted — so this is purely
 * to keep the table from accumulating rows for users who never return.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    @Scheduled(fixedDelayString = "${app.refresh-tokens.cleanup-interval-ms:3600000}")
    public void cleanUpExpired() {
        int deleted = refreshTokenRepository.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired refresh token(s)", deleted);
        }
    }
}
