package com.kommhub.controller;

import com.kommhub.model.db.User;
import com.kommhub.model.dto.response.AttachmentUploadResponse;
import com.kommhub.model.dto.response.ErrorResponse;
import com.kommhub.model.dto.summary.ConversationSummary;
import com.kommhub.security.SecurityUtil;
import com.kommhub.service.DirectMessageService;
import com.kommhub.websocket.messages.payloads.DmReceivedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dm")
public class DirectMessageController {

    private final DirectMessageService directMessageService;
    private final SecurityUtil securityUtil;

    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations() {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            List<ConversationSummary> conversations = directMessageService.getConversations(user.getUserId());
            return ResponseEntity.ok(conversations);
        } catch (Exception e) {
            log.error("Failed to get conversations: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    @GetMapping("/{partnerId}/messages")
    public ResponseEntity<?> getMessages(
            @PathVariable UUID partnerId,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");

            limit = Math.min(limit, 100);
            LocalDateTime cursor = before != null ? LocalDateTime.parse(before) : LocalDateTime.now().plusSeconds(1);
            List<DmReceivedPayload> messages = directMessageService.getMessagesBefore(user.getUserId(), partnerId, cursor, limit);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Failed to get DM messages with {}: {}", partnerId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    @PutMapping("/messages/{messageId}/reactions/{emoji}")
    public ResponseEntity<?> addReaction(@PathVariable UUID messageId, @PathVariable String emoji) {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            directMessageService.addReaction(messageId, user.getUserId(), emoji);
            return ResponseEntity.noContent().build();
        } catch (ResponseStatusException e) {
            return ErrorResponse.of((HttpStatus) e.getStatusCode(), e.getReason());
        } catch (Exception e) {
            log.error("Failed to add DM reaction: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    @DeleteMapping("/messages/{messageId}/reactions/{emoji}")
    public ResponseEntity<?> removeReaction(@PathVariable UUID messageId, @PathVariable String emoji) {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            directMessageService.removeReaction(messageId, user.getUserId(), emoji);
            return ResponseEntity.noContent().build();
        } catch (ResponseStatusException e) {
            return ErrorResponse.of((HttpStatus) e.getStatusCode(), e.getReason());
        } catch (Exception e) {
            log.error("Failed to remove DM reaction: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    @DeleteMapping("/{partnerId}")
    public ResponseEntity<?> hideConversation(@PathVariable UUID partnerId) {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            directMessageService.hideConversationForUser(user.getUserId(), partnerId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to hide conversation with {}: {}", partnerId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    @DeleteMapping("/{partnerId}/all")
    public ResponseEntity<?> deleteAllHistory(@PathVariable UUID partnerId) {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            directMessageService.deleteAllHistory(user.getUserId(), partnerId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to delete all history with {}: {}", partnerId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    @PutMapping("/{partnerId}/read")
    public ResponseEntity<?> markRead(@PathVariable UUID partnerId) {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            directMessageService.markConversationRead(user.getUserId(), partnerId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to mark conversation with {} as read: {}", partnerId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    @PostMapping("/attachments")
    public ResponseEntity<?> uploadAttachment(@RequestParam("file") MultipartFile file) {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            if (file.isEmpty()) return ErrorResponse.of(HttpStatus.BAD_REQUEST, "File is empty");
            if (file.getSize() > 10L * 1024 * 1024)
                return ErrorResponse.of(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds 10 MB limit");

            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
            AttachmentUploadResponse response = directMessageService.uploadDmAttachment(
                    user.getUserId(), file.getBytes(),
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment",
                    contentType);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Failed to upload DM attachment: {}", e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    @GetMapping("/messages/{messageId}/attachment")
    public ResponseEntity<?> downloadAttachment(@PathVariable UUID messageId) {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null) return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Not authenticated");
            return directMessageService.getAttachmentResource(messageId, user.getUserId());
        } catch (Exception e) {
            log.error("Failed to download DM attachment for message {}: {}", messageId, e.getMessage(), e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }
}
