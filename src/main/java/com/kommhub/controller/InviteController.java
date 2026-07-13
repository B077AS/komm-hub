package com.kommhub.controller;

import com.kommhub.model.dto.request.CreateInviteRequest;
import com.kommhub.model.dto.response.ErrorResponse;
import com.kommhub.model.dto.response.SuccessResponse;
import com.kommhub.security.SecurityUtil;
import com.kommhub.service.InviteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/invites")
public class InviteController {

    private final InviteService inviteService;
    private final SecurityUtil securityUtil;

    @PostMapping
    public ResponseEntity<?> createInvite(@RequestBody CreateInviteRequest req) {
        if (req.getServerId() == null) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Server ID is required");
        }
        try {
            UUID userId = securityUtil.getCurrentUserId();
            return ResponseEntity.status(HttpStatus.CREATED).body(inviteService.createInvite(userId, req));
        } catch (SecurityException e) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (NoSuchElementException e) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create invite: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create invite link");
        }
    }

    @GetMapping("/server/{serverId}")
    public ResponseEntity<?> listServerInvites(@PathVariable UUID serverId) {
        try {
            UUID userId = securityUtil.getCurrentUserId();
            return ResponseEntity.ok(inviteService.listServerInvites(userId, serverId));
        } catch (SecurityException e) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (NoSuchElementException e) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to list server invites: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch invite links");
        }
    }

    @DeleteMapping("/{inviteLinkId}")
    public ResponseEntity<?> deleteInvite(@PathVariable UUID inviteLinkId) {
        try {
            UUID userId = securityUtil.getCurrentUserId();
            inviteService.deleteInvite(userId, inviteLinkId);
            return SuccessResponse.of("Invite link deleted");
        } catch (SecurityException e) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (NoSuchElementException e) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete invite: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete invite link");
        }
    }

    @GetMapping("/{code}/info")
    public ResponseEntity<?> getInviteInfo(@PathVariable String code) {
        try {
            return ResponseEntity.ok(inviteService.getInviteInfo(code));
        } catch (NoSuchElementException e) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            return ErrorResponse.of(HttpStatus.GONE, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to get invite info: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch invite info");
        }
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<?> joinViaInvite(@PathVariable String code) {
        try {
            UUID userId = securityUtil.getCurrentUserId();
            String serverName = inviteService.useInvite(userId, code);
            return SuccessResponse.of("Successfully joined " + serverName + "!");
        } catch (NoSuchElementException e) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            return ErrorResponse.of(HttpStatus.GONE, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ErrorResponse.of(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to join via invite: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to join server");
        }
    }
}
