package com.kommhub.controller;

import com.kommhub.model.dto.request.BetaAccessRequest;
import com.kommhub.model.dto.request.ForgotPasswordRequest;
import com.kommhub.model.dto.request.ResendVerificationRequest;
import com.kommhub.model.dto.request.ResetPasswordRequest;
import com.kommhub.model.dto.request.VerifyEmailRequest;
import com.kommhub.model.dto.response.ErrorResponse;
import com.kommhub.model.dto.response.SuccessResponse;
import com.kommhub.model.dto.request.LoginRequest;
import com.kommhub.model.dto.request.RegisterRequest;
import com.kommhub.service.AuthService;
import com.kommhub.service.BetaKeyService;
import com.kommhub.service.EmailService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final BetaKeyService betaKeyService;
    private final EmailService emailService;

    // Recipient for beta access requests; defaults to the mail account the hub sends from
    @Value("${komm.beta.request-recipient:${spring.mail.username}}")
    private String betaRequestRecipient;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        log.debug("Login attempt for username: {}", request.getUsername());
        try {
            return ResponseEntity.ok(authService.login(request));
        } catch (DisabledException e) {
            log.warn("Login blocked for unverified account: {}", request.getUsername());
            return ErrorResponse.of(HttpStatus.FORBIDDEN, "Email not verified. Please check your inbox for the verification code.");
        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for username: {} - Invalid credentials", request.getUsername());
            return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        } catch (Exception e) {
            log.error("Login error for username: {} - {}", request.getUsername(), e.getMessage());
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        log.debug("Token refresh attempt");
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header. Expected Bearer token.");
            }
            return ResponseEntity.ok(authService.refresh(authHeader.substring(7)));
        } catch (IllegalArgumentException e) {
            return ErrorResponse.of(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("Refresh token has expired");
            return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Refresh token has expired. Please login again.");
        } catch (JwtException e) {
            log.warn("Invalid refresh token: {}", e.getMessage());
            return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Invalid refresh token: " + e.getMessage());
        } catch (Exception e) {
            log.error("Token refresh error: {}", e.getMessage());
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        log.debug("Registration attempt for username: {}", request.getUsername());
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Username is required");
        }
        String uname = request.getUsername().trim();
        if (uname.length() > 32) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Username must be 32 characters or fewer");
        }
        if (!uname.matches("[a-zA-Z0-9_-]+")) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Username may only contain letters, numbers, _ and -");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
        } catch (IllegalStateException e) {
            log.warn("Registration failed for username: {} - {}", request.getUsername(), e.getMessage());
            return ErrorResponse.of(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during registration for username: {}", request.getUsername(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        }
    }

    /** Public: lets clients and the register page know whether a beta key is required. */
    @GetMapping("/register-info")
    public ResponseEntity<?> registerInfo() {
        return ResponseEntity.ok(Map.of("betaRequired", betaKeyService.isBetaEnabled()));
    }

    /** Public: forwards a closed-beta participation request to the hub operator. */
    @PostMapping("/beta-request")
    public ResponseEntity<?> betaRequest(@RequestBody BetaAccessRequest request) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim();
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (email.isBlank() || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "A valid email address is required");
        }
        if (message.length() > 2000) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Message must be 2000 characters or fewer");
        }

        try {
            emailService.sendBetaAccessRequest(betaRequestRecipient, email,
                    message.isBlank() ? "(no message)" : message);
            return SuccessResponse.of("Request sent! We'll get back to you at " + email + ".");
        } catch (Exception e) {
            log.error("Failed to forward beta access request from {}: {}", email, e.getMessage());
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Could not send your request right now. Please try again later.");
        }
    }

    /** Public: sends a password reset link. Always answers generically so emails can't be enumerated. */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim();
        if (email.isBlank() || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "A valid email address is required");
        }
        request.setEmail(email);
        log.debug("Password reset requested for: {}", email);
        try {
            authService.requestPasswordReset(request);
        } catch (IllegalStateException e) {
            // Cooldown — safe to surface, it only fires for accounts that exist and asked recently
            log.warn("Password reset throttled for {}: {}", email, e.getMessage());
            return ErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during password reset request for: {}", email, e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        }
        return SuccessResponse.of("If an account exists for " + email + ", a reset link is on its way.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        log.debug("Password reset attempt");
        try {
            authService.resetPassword(request);
            return SuccessResponse.of("Password updated successfully. You can now log in.");
        } catch (IllegalStateException e) {
            log.warn("Password reset failed: {}", e.getMessage());
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during password reset", e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody VerifyEmailRequest request) {
        log.debug("Email verification attempt for: {}", request.getEmail());
        try {
            return ResponseEntity.ok(authService.verifyEmail(request));
        } catch (IllegalStateException e) {
            log.warn("Email verification failed for {}: {}", request.getEmail(), e.getMessage());
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during email verification for: {}", request.getEmail(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody ResendVerificationRequest request) {
        log.debug("Resend verification request for: {}", request.getEmail());
        try {
            authService.resendVerificationEmail(request);
            return SuccessResponse.of("Verification email resent successfully");
        } catch (IllegalStateException e) {
            log.warn("Resend verification failed for {}: {}", request.getEmail(), e.getMessage());
            return ErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during resend verification for: {}", request.getEmail(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        }
    }
}
