package com.kommhub.service;

import com.kommhub.model.db.BetaKey;
import com.kommhub.model.db.User;
import com.kommhub.model.dto.summary.BetaKeySummary;
import com.kommhub.repository.BetaKeyRepository;
import com.kommhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BetaKeyService {

    // No 0/O/1/I — keys get read aloud and typed by hand
    private static final char[] KEY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int MAX_KEYS_PER_BATCH = 100;

    private final BetaKeyRepository betaKeyRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${komm.beta.enabled:false}")
    private boolean betaEnabled;

    public boolean isBetaEnabled() {
        return betaEnabled;
    }

    @Transactional
    public List<BetaKeySummary> generateKeys(int count) {
        if (count < 1 || count > MAX_KEYS_PER_BATCH) {
            throw new IllegalArgumentException("Count must be between 1 and " + MAX_KEYS_PER_BATCH);
        }
        List<BetaKey> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            keys.add(BetaKey.builder().keyValue(randomKey()).build());
        }
        betaKeyRepository.saveAll(keys);
        log.info("Generated {} beta key(s)", count);
        return keys.stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<BetaKeySummary> listKeys() {
        return betaKeyRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public void deleteKey(UUID betaKeyId) {
        BetaKey key = betaKeyRepository.findById(betaKeyId)
                .orElseThrow(() -> new IllegalStateException("Beta key not found"));
        if (key.isUsed()) {
            throw new IllegalStateException("Cannot delete a beta key that has already been used");
        }
        betaKeyRepository.delete(key);
    }

    /**
     * Validates the key is present and unused. Call inside the registration
     * transaction, before the user is saved, then {@link #consume} after.
     */
    @Transactional
    public BetaKey validate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalStateException("A beta key is required to register during the closed beta");
        }
        BetaKey key = betaKeyRepository.findByKeyValue(rawKey.trim().toUpperCase())
                .orElseThrow(() -> new IllegalStateException("Invalid beta key"));
        if (key.isUsed()) {
            throw new IllegalStateException("This beta key has already been used");
        }
        return key;
    }

    @Transactional
    public void consume(BetaKey key, UUID userId) {
        key.setUsedByUserId(userId);
        key.setUsedAt(LocalDateTime.now());
        betaKeyRepository.save(key);
        log.info("Beta key {} consumed by user {}", key.getKeyValue(), userId);
    }

    private String randomKey() {
        StringBuilder sb = new StringBuilder("KOMM");
        for (int group = 0; group < 3; group++) {
            sb.append('-');
            for (int i = 0; i < 4; i++) {
                sb.append(KEY_ALPHABET[secureRandom.nextInt(KEY_ALPHABET.length)]);
            }
        }
        return sb.toString();
    }

    private BetaKeySummary toSummary(BetaKey key) {
        String usedByUsername = null;
        if (key.getUsedByUserId() != null) {
            usedByUsername = userRepository.findById(key.getUsedByUserId())
                    .map(User::getUsername)
                    .orElse("deleted user");
        }
        return BetaKeySummary.builder()
                .betaKeyId(key.getBetaKeyId())
                .keyValue(key.getKeyValue())
                .used(key.isUsed())
                .usedByUsername(usedByUsername)
                .usedAt(key.getUsedAt())
                .createdAt(key.getCreatedAt())
                .build();
    }
}
