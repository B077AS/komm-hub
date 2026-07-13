package com.kommhub.controller;

import com.kommhub.model.db.User;
import com.kommhub.model.dto.response.ErrorResponse;
import com.kommhub.model.dto.summary.FriendSummary;
import com.kommhub.security.SecurityUtil;
import com.kommhub.service.FriendService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/friends")
public class FriendController {

    private final SecurityUtil securityUtil;
    private final FriendService friendService;

    /** All accepted friends of the current user. */
    @GetMapping
    public ResponseEntity<?> getFriends() {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            List<FriendSummary> friends = friendService.getFriends(user);
            return ResponseEntity.ok(friends);
        } catch (Exception e) {
            log.error("Failed to get friends: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    /** Pending requests sent by the current user. */
    @GetMapping("/requests/sent")
    public ResponseEntity<?> getSentRequests() {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            List<FriendSummary> sent = friendService.getSentRequests(user);
            return ResponseEntity.ok(sent);
        } catch (Exception e) {
            log.error("Failed to get sent requests: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    /** Pending requests received by the current user. */
    @GetMapping("/requests/received")
    public ResponseEntity<?> getReceivedRequests() {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            List<FriendSummary> received = friendService.getReceivedRequests(user);
            return ResponseEntity.ok(received);
        } catch (Exception e) {
            log.error("Failed to get received requests: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    /** Send a friend request by target username. */
    @PostMapping("/request/{username}")
    public ResponseEntity<?> sendRequest(@PathVariable String username) {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            FriendSummary result = friendService.sendRequest(user, username);
            return ResponseEntity.ok(result);
        } catch (EntityNotFoundException e) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, "User not found: " + username);
        } catch (IllegalStateException e) {
            return ErrorResponse.of(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to send friend request to {}: {}", username, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    /** Accept a received friend request. */
    @PatchMapping("/{friendId}/accept")
    public ResponseEntity<?> acceptRequest(@PathVariable UUID friendId) {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            FriendSummary result = friendService.acceptRequest(user, friendId);
            return ResponseEntity.ok(result);
        } catch (EntityNotFoundException e) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, "Friend request not found");
        } catch (IllegalStateException e) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to accept friend request {}: {}", friendId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    /** Decline a received friend request. */
    @PatchMapping("/{friendId}/decline")
    public ResponseEntity<?> declineRequest(@PathVariable UUID friendId) {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            friendService.declineRequest(user, friendId);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, "Friend request not found");
        } catch (IllegalStateException e) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to decline friend request {}: {}", friendId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    /** Cancel a sent friend request. */
    @DeleteMapping("/{friendId}/cancel")
    public ResponseEntity<?> cancelRequest(@PathVariable UUID friendId) {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            friendService.cancelRequest(user, friendId);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, "Friend request not found");
        } catch (IllegalStateException e) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to cancel friend request {}: {}", friendId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    /** Remove an accepted friend. */
    @DeleteMapping("/{friendId}")
    public ResponseEntity<?> removeFriend(@PathVariable UUID friendId) {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            friendService.removeFriend(user, friendId);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, "Friend not found");
        } catch (IllegalStateException e) {
            return ErrorResponse.of(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to remove friend {}: {}", friendId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }
}