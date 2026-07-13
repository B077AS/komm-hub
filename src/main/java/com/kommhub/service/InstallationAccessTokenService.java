package com.kommhub.service;

import com.kommhub.model.db.InstallationAccessToken;
import com.kommhub.model.db.InstallationMember;
import com.kommhub.model.dto.summary.InstallationAccessTokenSummary;
import com.kommhub.repository.InstallationAccessTokenRepository;
import com.kommhub.repository.InstallationMemberRepository;
import com.kommhub.repository.InstallationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstallationAccessTokenService {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InstallationRepository installationRepository;
    private final InstallationAccessTokenRepository tokenRepository;
    private final InstallationMemberRepository memberRepository;

    public InstallationAccessTokenSummary generateToken(UUID installationId, UUID requesterId) {
        var installation = installationRepository.findById(installationId)
                .orElseThrow(() -> new NoSuchElementException("Installation not found"));

        if (!installation.getOwnerId().equals(requesterId)) {
            throw new SecurityException("Only the installation owner can generate access tokens");
        }

        String code = generateUniqueCode();
        InstallationAccessToken token = InstallationAccessToken.builder()
                .installationId(installationId)
                .code(code)
                .createdBy(requesterId)
                .build();
        token = tokenRepository.save(token);

        log.info("Access token generated for installation {} by user {}", installationId, requesterId);
        return toSummary(token);
    }

    public List<InstallationAccessTokenSummary> listTokens(UUID installationId, UUID requesterId) {
        var installation = installationRepository.findById(installationId)
                .orElseThrow(() -> new NoSuchElementException("Installation not found"));

        if (!installation.getOwnerId().equals(requesterId)) {
            throw new SecurityException("Only the installation owner can view access tokens");
        }

        return tokenRepository.findByInstallationIdAndUsedFalse(installationId).stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    public void deleteToken(UUID installationId, UUID tokenId, UUID requesterId) {
        var installation = installationRepository.findById(installationId)
                .orElseThrow(() -> new NoSuchElementException("Installation not found"));

        if (!installation.getOwnerId().equals(requesterId)) {
            throw new SecurityException("Only the installation owner can delete access tokens");
        }

        var token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new NoSuchElementException("Token not found"));

        if (!token.getInstallationId().equals(installationId)) {
            throw new SecurityException("Token does not belong to this installation");
        }

        tokenRepository.delete(token);
        log.info("Access token {} deleted from installation {} by user {}", tokenId, installationId, requesterId);
    }

    @Transactional
    public String joinViaToken(UUID userId, String code) {
        InstallationAccessToken token = tokenRepository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("Access token not found"));

        if (token.isUsed()) {
            throw new IllegalStateException("This access token has already been used");
        }

        var installation = installationRepository.findById(token.getInstallationId())
                .orElseThrow(() -> new NoSuchElementException("Installation not found"));

        if (installation.getOwnerId().equals(userId)) {
            throw new IllegalArgumentException("You are already the owner of this installation");
        }

        if (memberRepository.existsByInstallationIdAndUserId(token.getInstallationId(), userId)) {
            throw new IllegalArgumentException("You are already a member of this installation");
        }

        memberRepository.save(InstallationMember.builder()
                .installationId(token.getInstallationId())
                .userId(userId)
                .build());

        token.setUsed(true);
        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);

        log.info("User {} joined installation {} via access token", userId, token.getInstallationId());
        return installation.getInstallationName();
    }

    private InstallationAccessTokenSummary toSummary(InstallationAccessToken token) {
        return InstallationAccessTokenSummary.builder()
                .tokenId(token.getTokenId())
                .code(token.getCode())
                .createdAt(token.getCreatedAt())
                .build();
    }

    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            code = sb.toString();
        } while (tokenRepository.findByCode(code).isPresent());
        return code;
    }
}
