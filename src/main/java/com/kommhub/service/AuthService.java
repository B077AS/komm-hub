package com.kommhub.service;

import com.kommhub.config.SiteProperties;
import com.kommhub.model.db.Badge;
import com.kommhub.model.db.BetaKey;
import com.kommhub.model.db.EmailVerificationToken;
import com.kommhub.model.db.PasswordResetToken;
import com.kommhub.model.db.User;
import com.kommhub.model.dto.request.ForgotPasswordRequest;
import com.kommhub.model.dto.request.LoginRequest;
import com.kommhub.model.dto.request.RegisterRequest;
import com.kommhub.model.dto.request.ResendVerificationRequest;
import com.kommhub.model.dto.request.ResetPasswordRequest;
import com.kommhub.model.dto.request.VerifyEmailRequest;
import com.kommhub.model.db.RefreshToken;
import com.kommhub.model.dto.response.AuthResponse;
import com.kommhub.model.dto.response.RegisterResponse;
import com.kommhub.repository.EmailVerificationTokenRepository;
import com.kommhub.repository.PasswordResetTokenRepository;
import com.kommhub.repository.RefreshTokenRepository;
import com.kommhub.repository.UserRepository;
import com.kommhub.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int  VERIFICATION_EXPIRY_MINUTES    = 15;
    private static final long RESEND_COOLDOWN_SECONDS        = 60;
    private static final int  PASSWORD_RESET_EXPIRY_MINUTES  = 30;
    private static final int  MIN_PASSWORD_LENGTH            = 6;

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SiteProperties siteProperties;
    private final BetaKeyService betaKeyService;
    private final BadgeService badgeService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // authenticationManager throws DisabledException when isEnabled() == false
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsernameOrEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        log.info("User logged in successfully: {}", user.getUsername());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        Claims claims = jwtUtil.validateToken(refreshToken);

        if (jwtUtil.getTokenType(claims) != JwtUtil.TokenType.REFRESH) {
            throw new IllegalArgumentException("Invalid token type. Expected refresh token.");
        }

        UUID userId = UUID.fromString(claims.get("userId", String.class));

        // Rotation: each refresh token is single-use. A token with a valid
        // signature that is not in the store was either already rotated (reuse —
        // possibly stolen) or revoked, so kill every session for this user.
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hashToken(refreshToken)).orElse(null);
        if (stored == null) {
            refreshTokenRepository.deleteByUserId(userId);
            log.warn("Refresh token reuse detected for user {} — all refresh tokens revoked", userId);
            throw new IllegalArgumentException("Refresh token is no longer valid. Please login again.");
        }
        refreshTokenRepository.delete(stored);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        log.info("Tokens refreshed successfully for user: {}", user.getUsername());
        return buildAuthResponse(user);
    }

    /** Revokes the presented refresh token; unknown tokens are ignored. */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hashToken(refreshToken))
                .ifPresent(stored -> {
                    refreshTokenRepository.delete(stored);
                    log.info("Refresh token revoked for user {}", stored.getUserId());
                });
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // Check email first — if it belongs to an unverified account, resend the code
        // instead of rejecting, so the user can recover after closing the verification page
        Optional<User> existingByEmail = userRepository.findByEmail(request.getEmail());
        if (existingByEmail.isPresent()) {
            User existing = existingByEmail.get();
            if (existing.isEmailVerified()) {
                throw new IllegalStateException("Email already in use");
            }
            // Unverified account — generate a fresh code and resend
            String code = generateCode();
            LocalDateTime now = LocalDateTime.now();
            tokenRepository.findByUserId(existing.getUserId()).ifPresentOrElse(token -> {
                token.setCode(code);
                token.setExpiresAt(now.plusMinutes(VERIFICATION_EXPIRY_MINUTES));
                token.setLastResentAt(now);
                tokenRepository.save(token);
            }, () -> tokenRepository.save(EmailVerificationToken.builder()
                    .userId(existing.getUserId())
                    .code(code)
                    .expiresAt(now.plusMinutes(VERIFICATION_EXPIRY_MINUTES))
                    .lastResentAt(now)
                    .build()));
            emailService.sendVerificationEmail(existing.getEmail(), existing.getUsername(), code);
            log.info("Re-registration for unverified account, new code sent to: {}", existing.getEmail());
            return RegisterResponse.builder()
                    .username(existing.getUsername())
                    .email(existing.getEmail())
                    .message("A new verification code has been sent to your email. Please check your inbox.")
                    .build();
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalStateException("Username already taken");
        }

        // Closed beta: a new account requires an unused beta key. The unverified
        // re-registration path above deliberately skips this — that account
        // already consumed a key when it was first created.
        BetaKey betaKey = betaKeyService.isBetaEnabled()
                ? betaKeyService.validate(request.getBetaKey())
                : null;

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .status(User.UserStatus.OFFLINE)
                .emailVerified(false)
                .build();

        userRepository.save(user);

        if (betaKey != null) {
            betaKeyService.consume(betaKey, user.getUserId());
            badgeService.awardByCode(user.getUserId(), Badge.CODE_BETA);
        }

        String code = generateCode();
        LocalDateTime now = LocalDateTime.now();
        tokenRepository.save(EmailVerificationToken.builder()
                .userId(user.getUserId())
                .code(code)
                .expiresAt(now.plusMinutes(VERIFICATION_EXPIRY_MINUTES))
                .lastResentAt(now)
                .build());

        emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), code);
        log.info("User registered, verification email sent to: {}", user.getEmail());

        return RegisterResponse.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .message("Verification email sent. Please check your inbox.")
                .build();
    }

    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException("No account found with this email"));

        if (user.isEmailVerified()) {
            throw new IllegalStateException("Email is already verified");
        }

        EmailVerificationToken token = tokenRepository.findByUserId(user.getUserId())
                .orElseThrow(() -> new IllegalStateException(
                        "No verification code found. Please request a new one."));

        if (LocalDateTime.now().isAfter(token.getExpiresAt())) {
            tokenRepository.delete(token);
            throw new IllegalStateException("Verification code has expired. Please request a new one.");
        }

        if (!token.getCode().equals(request.getCode())) {
            throw new IllegalStateException("Invalid verification code");
        }

        user.setEmailVerified(true);
        user.setEmailVerifiedAt(LocalDateTime.now());
        userRepository.save(user);
        tokenRepository.delete(token);

        log.info("Email verified for user: {}", user.getUsername());
        return buildAuthResponse(user);
    }

    @Transactional
    public void resendVerificationEmail(ResendVerificationRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException("No account found with this email"));

        if (user.isEmailVerified()) {
            throw new IllegalStateException("Email is already verified");
        }

        LocalDateTime now = LocalDateTime.now();
        String code = generateCode();

        Optional<EmailVerificationToken> existingOpt = tokenRepository.findByUserId(user.getUserId());
        if (existingOpt.isPresent()) {
            EmailVerificationToken existing = existingOpt.get();
            LocalDateTime cooldownEnd = existing.getLastResentAt().plusSeconds(RESEND_COOLDOWN_SECONDS);
            if (now.isBefore(cooldownEnd)) {
                long secondsLeft = Duration.between(now, cooldownEnd).getSeconds();
                throw new IllegalStateException("Please wait " + secondsLeft + " seconds before requesting a new code");
            }
            existing.setCode(code);
            existing.setExpiresAt(now.plusMinutes(VERIFICATION_EXPIRY_MINUTES));
            existing.setLastResentAt(now);
            tokenRepository.save(existing);
        } else {
            tokenRepository.save(EmailVerificationToken.builder()
                    .userId(user.getUserId())
                    .code(code)
                    .expiresAt(now.plusMinutes(VERIFICATION_EXPIRY_MINUTES))
                    .lastResentAt(now)
                    .build());
        }

        emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), code);
        log.info("Verification email resent to: {}", user.getEmail());
    }

    /**
     * Starts the forgot-password flow. Deliberately does not reveal whether the
     * email belongs to an account — callers always get a generic success message.
     */
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isEmpty() || !userOpt.get().isEmailVerified()) {
            log.info("Password reset requested for unknown or unverified email: {}", request.getEmail());
            return;
        }
        User user = userOpt.get();

        String token = generateResetToken();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(PASSWORD_RESET_EXPIRY_MINUTES);

        passwordResetTokenRepository.findByUserId(user.getUserId()).ifPresentOrElse(existing -> {
            LocalDateTime cooldownEnd = existing.getLastSentAt().plusSeconds(RESEND_COOLDOWN_SECONDS);
            if (now.isBefore(cooldownEnd)) {
                long secondsLeft = Duration.between(now, cooldownEnd).getSeconds();
                throw new IllegalStateException("Please wait " + secondsLeft + " seconds before requesting another reset email");
            }
            existing.setToken(token);
            existing.setExpiresAt(expiresAt);
            existing.setLastSentAt(now);
            passwordResetTokenRepository.save(existing);
        }, () -> passwordResetTokenRepository.save(PasswordResetToken.builder()
                .userId(user.getUserId())
                .token(token)
                .expiresAt(expiresAt)
                .lastSentAt(now)
                .build()));

        String resetLink = siteProperties.getBaseUrl() + "/reset-password?token=" + token;
        emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetLink);
        log.info("Password reset email sent to: {}", user.getEmail());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (request.getToken() == null || request.getToken().isBlank()) {
            throw new IllegalStateException("Reset token is missing");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        PasswordResetToken token = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new IllegalStateException(
                        "This reset link is invalid or has already been used. Please request a new one."));

        if (LocalDateTime.now().isAfter(token.getExpiresAt())) {
            passwordResetTokenRepository.delete(token);
            throw new IllegalStateException("This reset link has expired. Please request a new one.");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalStateException("Account no longer exists"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        passwordResetTokenRepository.delete(token);

        log.info("Password reset completed for user: {}", user.getUsername());
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    /** 32 random bytes, URL-safe base64 (43 chars) — safe to embed in the emailed link. */
    private String generateResetToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private AuthResponse buildAuthResponse(User user) {
        String refreshToken = jwtUtil.generateRefreshToken(user);
        storeRefreshToken(user.getUserId(), refreshToken);
        return AuthResponse.builder()
                .accessToken(jwtUtil.generateAccessToken(user))
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900)
                .build();
    }

    private void storeRefreshToken(UUID userId, String refreshToken) {
        LocalDateTime now = LocalDateTime.now();
        // Drop this user's expired rows right away; RefreshTokenCleanupService
        // sweeps the rest (users who never come back) on a schedule
        refreshTokenRepository.deleteByUserIdAndExpiresAtBefore(userId, now);
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash(hashToken(refreshToken))
                .expiresAt(now.plusSeconds(jwtUtil.getRefreshTokenExpiration()))
                .build());
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
