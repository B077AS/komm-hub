package com.kommhub.controller;

import com.kommhub.model.dto.request.BadgeCreateRequest;
import com.kommhub.model.dto.response.ErrorResponse;
import com.kommhub.service.BadgeIconService;
import com.kommhub.service.BadgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Badge management — SUPER_ADMIN only. Used by the website dashboard to create
 * custom badges, and to generate/inspect redemption tokens.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/badges")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class BadgeAdminController {

    private final BadgeService badgeService;
    private final BadgeIconService badgeIconService;

    @GetMapping
    public ResponseEntity<?> listBadges() {
        try {
            return ResponseEntity.ok(badgeService.listBadgesForAdmin());
        } catch (Exception e) {
            log.error("Failed to list badges: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        }
    }

    @PostMapping
    public ResponseEntity<?> createBadge(@RequestBody BadgeCreateRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(badgeService.createBadge(request));
        } catch (IllegalArgumentException e) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create badge: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        }
    }

    @DeleteMapping("/{badgeId}")
    public ResponseEntity<?> deleteBadge(@PathVariable UUID badgeId) {
        try {
            badgeService.deleteBadge(badgeId);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ErrorResponse.of(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete badge {}: {}", badgeId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        }
    }

    /** Full MDI icon catalog ({n: literal, c: hex codepoint}) for the picker. */
    @GetMapping("/icons")
    public ResponseEntity<?> listIcons() {
        return ResponseEntity.ok(badgeIconService.getCatalog());
    }
}
