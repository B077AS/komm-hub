package com.kommhub.controller;

import com.kommhub.model.dto.request.GenerateBetaKeysRequest;
import com.kommhub.model.dto.response.ErrorResponse;
import com.kommhub.service.BetaKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Beta key management — SUPER_ADMIN only. Used by the client's profile page
 * to generate and inspect closed-beta registration keys.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/beta-keys")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class BetaKeyAdminController {

    private final BetaKeyService betaKeyService;

    @GetMapping
    public ResponseEntity<?> listKeys() {
        try {
            return ResponseEntity.ok(betaKeyService.listKeys());
        } catch (Exception e) {
            log.error("Failed to list beta keys: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        }
    }

    @PostMapping
    public ResponseEntity<?> generateKeys(@RequestBody GenerateBetaKeysRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(betaKeyService.generateKeys(request.getCount()));
        } catch (IllegalArgumentException e) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to generate beta keys: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        }
    }

    @DeleteMapping("/{betaKeyId}")
    public ResponseEntity<?> deleteKey(@PathVariable UUID betaKeyId) {
        try {
            betaKeyService.deleteKey(betaKeyId);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ErrorResponse.of(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete beta key {}: {}", betaKeyId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        }
    }
}
