package com.kommhub.service;

import com.kommhub.model.db.Badge;
import com.kommhub.model.db.BadgeToken;
import com.kommhub.model.db.BetaKey;
import com.kommhub.model.db.User;
import com.kommhub.model.db.UserBadge;
import com.kommhub.model.dto.request.BadgeCreateRequest;
import com.kommhub.model.dto.summary.BadgeSummary;
import com.kommhub.model.dto.summary.BadgeTokenSummary;
import com.kommhub.repository.BadgeRepository;
import com.kommhub.repository.BadgeTokenRepository;
import com.kommhub.repository.BetaKeyRepository;
import com.kommhub.repository.UserBadgeRepository;
import com.kommhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeService implements ApplicationRunner {

    // Same alphabet as beta keys — no 0/O/1/I, tokens get typed by hand
    private static final char[] TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final BadgeTokenRepository badgeTokenRepository;
    private final BetaKeyRepository betaKeyRepository;
    private final UserRepository userRepository;
    private final BadgeIconService badgeIconService;
    private final SecureRandom secureRandom = new SecureRandom();

    // ── Seeding & automatic awards ──────────────────────────────────────────

    /**
     * Seeds the two SYSTEM badges and backfills the beta badge for every
     * account that consumed a beta key before this feature existed. Idempotent.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedSystemBadge(Badge.CODE_SUPER_ADMIN, "Super Admin",
                "Operates this Komm hub", "mdi2s-shield-crown", "#F0A830", 0);
        Badge beta = seedSystemBadge(Badge.CODE_BETA, "Beta Tester",
                "Joined during the closed beta", "mdi2f-flask-outline", "#A371F7", 1);

        int backfilled = 0;
        for (BetaKey key : betaKeyRepository.findAll()) {
            if (key.getUsedByUserId() != null
                    && userRepository.existsById(key.getUsedByUserId())
                    && !userBadgeRepository.existsByUserIdAndBadgeId(key.getUsedByUserId(), beta.getBadgeId())) {
                userBadgeRepository.save(UserBadge.builder()
                        .userId(key.getUsedByUserId())
                        .badgeId(beta.getBadgeId())
                        .build());
                backfilled++;
            }
        }
        if (backfilled > 0) {
            log.info("Backfilled beta badge for {} existing user(s)", backfilled);
        }
    }

    private Badge seedSystemBadge(String code, String name, String description,
                                  String icon, String color, int position) {
        return badgeRepository.findByCode(code).orElseGet(() -> {
            Badge badge = badgeRepository.save(Badge.builder()
                    .code(code)
                    .name(name)
                    .description(description)
                    .icon(icon)
                    .color(color)
                    .type(Badge.BadgeType.SYSTEM)
                    .position(position)
                    .build());
            log.info("Seeded system badge {}", code);
            return badge;
        });
    }

    /** Awards a badge by code, silently skipping if already held. */
    @Transactional
    public void awardByCode(UUID userId, String code) {
        badgeRepository.findByCode(code).ifPresentOrElse(badge -> {
            if (!userBadgeRepository.existsByUserIdAndBadgeId(userId, badge.getBadgeId())) {
                userBadgeRepository.save(UserBadge.builder()
                        .userId(userId)
                        .badgeId(badge.getBadgeId())
                        .build());
                log.info("Awarded badge {} to user {}", code, userId);
            }
        }, () -> log.warn("Cannot award badge {}: not found", code));
    }

    /** Removes all badge rows for a deleted user account. */
    @Transactional
    public void removeAllForUser(UUID userId) {
        userBadgeRepository.deleteByUserId(userId);
    }

    // ── Reads ───────────────────────────────────────────────────────────────

    /**
     * All badges shown on a user's profile: materialized awards plus the
     * virtual SUPER_ADMIN badge derived from the user's role.
     */
    @Transactional(readOnly = true)
    public List<BadgeSummary> getBadgesForUser(User user) {
        List<BadgeSummary> result = new ArrayList<>();

        if (user.getRole() == User.Role.SUPER_ADMIN) {
            badgeRepository.findByCode(Badge.CODE_SUPER_ADMIN)
                    .ifPresent(b -> result.add(toSummary(b, null, false)));
        }

        for (UserBadge ub : userBadgeRepository.findAllWithBadgeByUserId(user.getUserId())) {
            result.add(toSummary(ub.getBadge(), ub.getAwardedAt(), false));
        }

        result.sort(Comparator.comparingInt(BadgeSummary::getPosition));
        return result;
    }

    @Transactional(readOnly = true)
    public List<BadgeSummary> listBadgesForAdmin() {
        return badgeRepository.findAllByOrderByPositionAscCreatedAtAsc().stream()
                .map(b -> {
                    BadgeSummary s = toSummary(b, null, true);
                    if (Badge.CODE_SUPER_ADMIN.equals(b.getCode())) {
                        s.setUserCount(userRepository.countByRole(User.Role.SUPER_ADMIN));
                    }
                    if (b.getType() == Badge.BadgeType.CUSTOM) {
                        badgeTokenRepository.findFirstByBadgeIdOrderByCreatedAtDesc(b.getBadgeId())
                                .ifPresent(t -> s.setToken(toTokenSummary(t, b)));
                    }
                    return s;
                })
                .toList();
    }

    // ── Token redemption ────────────────────────────────────────────────────

    @Transactional
    public BadgeSummary redeem(User user, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Token is required");
        }
        BadgeToken token = badgeTokenRepository.findByTokenValue(rawToken.trim().toUpperCase())
                .filter(t -> !t.isExpired() && !t.isExhausted())
                // Uniform message — don't reveal whether a token exists, expired or ran out
                .orElseThrow(() -> new IllegalStateException("Invalid or expired token"));

        Badge badge = badgeRepository.findById(token.getBadgeId())
                .orElseThrow(() -> new IllegalStateException("Invalid or expired token"));

        if (userBadgeRepository.existsByUserIdAndBadgeId(user.getUserId(), badge.getBadgeId())) {
            throw new IllegalStateException("You already have this badge");
        }

        UserBadge awarded = userBadgeRepository.save(UserBadge.builder()
                .userId(user.getUserId())
                .badgeId(badge.getBadgeId())
                .build());

        token.setUsedCount(token.getUsedCount() + 1);
        badgeTokenRepository.save(token);

        log.info("User {} redeemed badge token for badge {}", user.getUserId(), badge.getCode());
        return toSummary(badge, awarded.getAwardedAt(), false);
    }

    // ── Admin operations ────────────────────────────────────────────────────

    @Transactional
    public BadgeSummary createBadge(BadgeCreateRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Badge name is required");
        }
        String name = request.getName().trim();
        if (name.length() > 50) {
            throw new IllegalArgumentException("Badge name must be 50 characters or fewer");
        }
        if (!badgeIconService.isValid(request.getIcon())) {
            throw new IllegalArgumentException("Unknown icon code: " + request.getIcon());
        }
        String color = request.getColor();
        if (color != null && !color.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Color must be in #RRGGBB format");
        }

        String code = slugify(name);
        if (badgeRepository.existsByCode(code)) {
            throw new IllegalArgumentException("A badge with a similar name already exists");
        }

        Badge badge = badgeRepository.save(Badge.builder()
                .code(code)
                .name(name)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .icon(request.getIcon())
                .color(color != null ? color.toUpperCase() : null)
                .type(Badge.BadgeType.CUSTOM)
                .position(100)
                .build());
        log.info("Created custom badge {} ({})", badge.getName(), badge.getCode());

        // 1 badge = 1 token: the badge's single redemption token is born with
        // it, using the limits from the create form. Same transaction — an
        // invalid expiry rolls the badge back too.
        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Expiry must be in the future");
        }
        Integer maxUses = request.getMaxUses() != null && request.getMaxUses() > 0
                ? request.getMaxUses()
                : null;
        BadgeToken token = badgeTokenRepository.save(BadgeToken.builder()
                .tokenValue(randomToken())
                .badgeId(badge.getBadgeId())
                .maxUses(maxUses)
                .expiresAt(request.getExpiresAt())
                .build());

        BadgeSummary summary = toSummary(badge, null, true);
        summary.setToken(toTokenSummary(token, badge));
        return summary;
    }

    @Transactional
    public void deleteBadge(UUID badgeId) {
        Badge badge = badgeRepository.findById(badgeId)
                .orElseThrow(() -> new IllegalStateException("Badge not found"));
        if (badge.getType() == Badge.BadgeType.SYSTEM) {
            throw new IllegalStateException("System badges cannot be deleted");
        }
        badgeTokenRepository.deleteByBadgeId(badgeId);
        userBadgeRepository.deleteByBadgeId(badgeId);
        badgeRepository.delete(badge);
        log.info("Deleted custom badge {} ({})", badge.getName(), badge.getCode());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String randomToken() {
        StringBuilder sb = new StringBuilder("BADGE");
        for (int group = 0; group < 3; group++) {
            sb.append('-');
            for (int i = 0; i < 4; i++) {
                sb.append(TOKEN_ALPHABET[secureRandom.nextInt(TOKEN_ALPHABET.length)]);
            }
        }
        return sb.toString();
    }

    private static String slugify(String name) {
        return name.trim().toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private BadgeSummary toSummary(Badge badge, LocalDateTime awardedAt, boolean withUserCount) {
        return BadgeSummary.builder()
                .badgeId(badge.getBadgeId())
                .code(badge.getCode())
                .name(badge.getName())
                .description(badge.getDescription())
                .icon(badge.getIcon())
                .iconCodepoint(badgeIconService.codepointHex(badge.getIcon()))
                .color(badge.getColor())
                .type(badge.getType())
                .position(badge.getPosition())
                .awardedAt(awardedAt)
                .userCount(withUserCount ? userBadgeRepository.countByBadgeId(badge.getBadgeId()) : null)
                .build();
    }

    private BadgeTokenSummary toTokenSummary(BadgeToken token, Badge badge) {
        return BadgeTokenSummary.builder()
                .badgeTokenId(token.getBadgeTokenId())
                .tokenValue(token.getTokenValue())
                .badgeId(token.getBadgeId())
                .badgeName(badge != null ? badge.getName() : null)
                .maxUses(token.getMaxUses())
                .usedCount(token.getUsedCount())
                .expiresAt(token.getExpiresAt())
                .createdAt(token.getCreatedAt())
                .expired(token.isExpired())
                .exhausted(token.isExhausted())
                .build();
    }
}
