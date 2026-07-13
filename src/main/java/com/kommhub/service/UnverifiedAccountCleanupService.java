package com.kommhub.service;

import com.kommhub.model.db.User;
import com.kommhub.repository.BetaKeyRepository;
import com.kommhub.repository.EmailVerificationTokenRepository;
import com.kommhub.repository.UserBadgeRepository;
import com.kommhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Deletes accounts that never completed email verification, freeing their
 * username and email for a fresh registration (e.g. after an email typo).
 * The beta key consumed at registration is deleted along with the account —
 * abandoned signups don't get to keep an invite.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnverifiedAccountCleanupService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final BetaKeyRepository betaKeyRepository;
    private final UserBadgeRepository userBadgeRepository;

    @Value("${app.unverified-accounts.retention-days:7}")
    private int retentionDays;

    @Transactional
    @Scheduled(fixedDelayString = "${app.unverified-accounts.cleanup-interval-ms:3600000}")
    public void cleanUpUnverifiedAccounts() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<User> stale = userRepository.findAllByEmailVerifiedFalseAndCreatedAtBefore(cutoff);
        if (stale.isEmpty()) {
            return;
        }
        for (User user : stale) {
            tokenRepository.deleteByUserId(user.getUserId());
            betaKeyRepository.deleteByUsedByUserId(user.getUserId());
            userBadgeRepository.deleteByUserId(user.getUserId());
            userRepository.delete(user);
        }
        log.info("Cleaned up {} unverified account(s) older than {} day(s)", stale.size(), retentionDays);
    }
}
