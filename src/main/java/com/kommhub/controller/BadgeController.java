package com.kommhub.controller;

import com.kommhub.model.db.User;
import com.kommhub.model.dto.request.RedeemBadgeTokenRequest;
import com.kommhub.model.dto.response.ErrorResponse;
import com.kommhub.security.SecurityUtil;
import com.kommhub.service.BadgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User-facing badge endpoints. Badge lists ride along on the user summaries
 * (/api/users/me and /api/users/{id}/summary); this controller only handles
 * token redemption from the client's edit-profile modal.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/badges")
public class BadgeController {

    private final SecurityUtil securityUtil;
    private final BadgeService badgeService;

    @PostMapping("/redeem")
    public ResponseEntity<?> redeem(@RequestBody RedeemBadgeTokenRequest request) {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) {
                return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            }
            return ResponseEntity.ok(badgeService.redeem(user, request.getToken()));
        } catch (IllegalArgumentException e) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return ErrorResponse.of(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to redeem badge token: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        }
    }
}
