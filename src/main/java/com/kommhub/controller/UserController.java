package com.kommhub.controller;

import com.kommhub.model.db.User;
import com.kommhub.model.dto.request.AvStateUpdateRequest;
import com.kommhub.model.dto.request.StatusUpdateRequest;
import com.kommhub.model.dto.request.UserUpdateRequest;
import com.kommhub.model.dto.response.ErrorResponse;
import com.kommhub.model.dto.response.SuccessResponse;
import com.kommhub.model.dto.response.UserBatchCacheResponse;
import com.kommhub.model.dto.response.UserCacheResponse;
import com.kommhub.model.dto.response.UserLookupResponse;
import com.kommhub.model.dto.response.UserStatusDto;
import com.kommhub.model.dto.summary.ChannelUserSummary;
import com.kommhub.model.dto.summary.MainUserSummary;
import com.kommhub.model.dto.summary.UserSummary;
import com.kommhub.repository.UserRepository;
import com.kommhub.security.SecurityUtil;
import com.kommhub.service.AvatarBroadcastService;
import com.kommhub.service.PresenceService;
import com.kommhub.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final SecurityUtil securityUtil;
    private final UserService userService;
    private final UserRepository userRepository;
    private final PresenceService presenceService;
    private final AvatarBroadcastService avatarBroadcastService;

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        try {
            User user = securityUtil.getCurrentUser();

            if (user == null) {
                return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            }

            MainUserSummary mainUserSummary = userService.toDto(user);
            log.info("Successfully retrieved user: {}", user.getUsername());
            return ResponseEntity.ok(mainUserSummary);

        } catch (Exception e) {
            log.error("Failed to retrieve current user: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing request: " + e.getMessage());
        }
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateCurrentUser(@RequestBody UserUpdateRequest dto) {
        if (dto.getUsername() != null && !dto.getUsername().isBlank()) {
            String uname = dto.getUsername().trim();
            if (uname.length() > 32) {
                return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Username must be 32 characters or fewer");
            }
            if (!uname.matches("[a-zA-Z0-9_-]+")) {
                return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Username may only contain letters, numbers, _ and -");
            }
        }
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) {
                return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            }
            if (dto.getEmail() != null && !dto.getEmail().trim().equalsIgnoreCase(user.getEmail())) {
                return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Email cannot be changed");
            }
            MainUserSummary updated = userService.updateUser(user, dto);
            if (dto.getAvatar() != null) {
                avatarBroadcastService.broadcastAvatarUpdate(user);
            }
            log.info("Successfully updated user: {}", user.getUsername());
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Failed to update current user: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing request: " + e.getMessage());
        }
    }

    @PatchMapping("/me/status")
    public ResponseEntity<?> updateCurrentUserStatus(@RequestBody StatusUpdateRequest dto) {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) {
                return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            }
            if (dto.getStatus() == null) {
                return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Status is required");
            }
            MainUserSummary updated = userService.updateStatus(user, dto.getStatus());
            presenceService.broadcastStatusToCoMembers(user.getUserId(), dto.getStatus());
            log.info("Successfully updated status for user: {}", user.getUsername());
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Failed to update status for current user: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing request: " + e.getMessage());
        }
    }

    @PatchMapping("/me/av-state")
    public ResponseEntity<?> updateCurrentUserAvState(@RequestBody AvStateUpdateRequest dto) {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) {
                return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            }
            if (dto.getMicEnabled() == null || dto.getSpeakerEnabled() == null) {
                return ErrorResponse.of(HttpStatus.BAD_REQUEST, "micEnabled and speakerEnabled are required");
            }
            userService.updateAvState(user, dto.getMicEnabled(), dto.getSpeakerEnabled());
            return SuccessResponse.of("Audio state updated");
        } catch (Exception e) {
            log.error("Failed to update audio state for current user: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing request: " + e.getMessage());
        }
    }

    @GetMapping("/{userId}/avatar")
    public ResponseEntity<?> getUserAvatar(@PathVariable UUID userId) {
        try {
            UserCacheResponse avatar = userService.getAvatar(userId);
            return ResponseEntity.ok(avatar);
        } catch (EntityNotFoundException e) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, "User not found: " + userId);
        } catch (Exception e) {
            log.error("Failed to retrieve avatar for user {}: {}", userId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing request: " + e.getMessage());
        }
    }

    @GetMapping("/{userId}/summary")
    public ResponseEntity<?> getUserSummary(@PathVariable UUID userId) {
        try {
            User currentUser = securityUtil.getCurrentUser();
            if (currentUser == null) {
                return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            }
            User target = userRepository.findById(userId)
                    .orElse(null);
            if (target == null) {
                return ErrorResponse.of(HttpStatus.NOT_FOUND, "User not found: " + userId);
            }
            UserSummary summary = userService.toUserSummary(target, currentUser);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Failed to retrieve summary for user {}: {}", userId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing request: " + e.getMessage());
        }
    }

    @GetMapping("/by-username/{username}")
    public ResponseEntity<?> getUserByUsername(@PathVariable String username) {
        try {
            UserLookupResponse result = userService.findByUsername(username);
            if (result == null) return ErrorResponse.of(HttpStatus.NOT_FOUND, "User not found: " + username);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to find user by username {}: {}", username, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/channel-user/{userId}")
    public ResponseEntity<?> getChannelUserSummary(@PathVariable UUID userId) {
        try {
            ChannelUserSummary summary = userService.toChannelDto(userId);
            return ResponseEntity.ok(summary);
        } catch (EntityNotFoundException e) {
            return ErrorResponse.of(HttpStatus.NOT_FOUND, "User not found: " + userId);
        } catch (Exception e) {
            log.error("Failed to retrieve channel summary for user {}: {}", userId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing request: " + e.getMessage());
        }
    }

    @GetMapping("/batch-avatars")
    public ResponseEntity<?> getBatchAvatars(@RequestParam String ids) {
        try {
            if (ids == null || ids.isBlank()) return ResponseEntity.ok(List.of());
            List<UUID> uuids = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(UUID::fromString)
                    .toList();
            List<UserBatchCacheResponse> result = userService.getBatchAvatars(uuids);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Invalid UUID in ids parameter");
        } catch (Exception e) {
            log.error("Failed to retrieve batch avatars: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/batch")
    public ResponseEntity<?> getUsersBatch(@RequestParam String ids) {
        try {
            if (ids == null || ids.isBlank()) return ResponseEntity.ok(List.of());
            List<UUID> uuids = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(UUID::fromString)
                    .toList();
            List<UserStatusDto> result = uuids.stream()
                    .map(id -> userRepository.findById(id).orElse(null))
                    .filter(u -> u != null)
                    .map(userService::toStatusDto)
                    .toList();
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Invalid UUID in ids parameter");
        } catch (Exception e) {
            log.error("Failed to retrieve batch user info: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}