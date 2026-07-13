package com.kommhub.service;

import com.kommhub.model.db.DirectMessage;
import com.kommhub.model.db.DirectMessageAttachment;
import com.kommhub.model.db.DirectMessageReaction;
import com.kommhub.model.db.DmConversationHidden;
import com.kommhub.model.db.DmConversationRead;
import com.kommhub.model.db.PendingDmAttachment;
import com.kommhub.model.dto.response.AttachmentUploadResponse;
import com.kommhub.model.dto.summary.ConversationSummary;
import com.kommhub.model.dto.response.ErrorResponse;
import com.kommhub.repository.DirectMessageAttachmentRepository;
import com.kommhub.repository.DirectMessageReactionRepository;
import com.kommhub.repository.DirectMessageRepository;
import com.kommhub.repository.DmConversationHiddenRepository;
import com.kommhub.repository.DmConversationReadRepository;
import com.kommhub.repository.PendingDmAttachmentRepository;
import com.kommhub.repository.UserRepository;
import com.kommhub.websocket.senders.AppMessageSender;
import com.kommhub.websocket.messages.WsAppMessage;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.DmConversationDeletedPayload;
import com.kommhub.websocket.messages.payloads.DmConversationHiddenPayload;
import com.kommhub.websocket.messages.payloads.DmReactionAddedPayload;
import com.kommhub.websocket.messages.payloads.DmReactionRemovedPayload;
import com.kommhub.websocket.messages.payloads.DmReceivedPayload;
import com.kommhub.websocket.messages.payloads.DmSentPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectMessageService {

    private final DirectMessageRepository messageRepository;
    private final DirectMessageAttachmentRepository attachmentRepository;
    private final DirectMessageReactionRepository reactionRepository;
    private final DmConversationHiddenRepository hiddenRepository;
    private final DmConversationReadRepository readRepository;
    private final UserRepository userRepository;
    private final AppMessageSender appMessageSender;
    private final PendingDmAttachmentRepository pendingAttachmentRepository;

    @Value("${app.dm.attachments.dir}")
    private String attachmentsBaseDir;

    // ── Send ──────────────────────────────────────────────────────────────────

    public DmReceivedPayload save(UUID senderId, DmSentPayload sent) {
        DirectMessage saved = messageRepository.save(DirectMessage.builder()
                .senderId(senderId)
                .recipientId(sent.getRecipientId())
                .content(sent.getContent())
                .sentAt(LocalDateTime.now())
                .isEdited(false)
                .hasAttachments(sent.isHasAttachments())
                .repliedToId(sent.getRepliedToId())
                .messageType(sent.getMessageType() != null ? sent.getMessageType() : DirectMessage.MessageType.TEXT)
                .codeLanguage(sent.getCodeLanguage())
                .build());

        String persistedFile64 = null;
        String persistedFileName = null;
        String persistedFileType = null;
        long persistedFileSize = 0;

        if (sent.isHasAttachments() && sent.getAttachmentId() != null) {
            PendingDmAttachment pending = pendingAttachmentRepository.findById(sent.getAttachmentId()).orElse(null);
            if (pending == null || !pending.getUploaderId().equals(senderId)) {
                log.warn("DM save: invalid or missing pending attachment {} for senderId={}", sent.getAttachmentId(), senderId);
            } else {
                DirectMessageAttachment attachment = DirectMessageAttachment.builder()
                        .messageId(saved.getMessageId())
                        .filePath(pending.getFilePath())
                        .fileName(pending.getFileName())
                        .fileSize(pending.getFileSize())
                        .fileType(pending.getFileType())
                        .build();
                attachmentRepository.save(attachment);
                pendingAttachmentRepository.delete(pending);

                persistedFileName = pending.getFileName();
                persistedFileType = pending.getFileType();
                persistedFileSize = pending.getFileSize();
                persistedFile64 = isImageType(persistedFileType) ? readFileAsBase64(pending.getFilePath()) : null;
            }
        }

        UUID replyToSenderId = null;
        String replyToContent = null;
        DirectMessage.MessageType replyToMessageType = null;
        boolean replyToHasAttachments = false;
        String replyToFileName = null;
        String replyToFileType = null;
        if (saved.getRepliedToId() != null) {
            DirectMessage repliedTo = messageRepository.findById(saved.getRepliedToId()).orElse(null);
            if (repliedTo != null) {
                replyToSenderId = repliedTo.getSenderId();
                replyToContent = repliedTo.getContent();
                replyToMessageType = repliedTo.getMessageType();
                replyToHasAttachments = Boolean.TRUE.equals(repliedTo.getHasAttachments());
                if (replyToHasAttachments) {
                    List<DirectMessageAttachment> replyAtts = attachmentRepository
                            .findByMessageIdIn(List.of(saved.getRepliedToId()));
                    if (!replyAtts.isEmpty()) {
                        replyToFileName = replyAtts.get(0).getFileName();
                        replyToFileType = replyAtts.get(0).getFileType();
                    }
                }
            }
        }

        return DmReceivedPayload.builder()
                .messageId(saved.getMessageId())
                .senderId(saved.getSenderId())
                .recipientId(saved.getRecipientId())
                .content(saved.getContent())
                .sentAt(saved.getSentAt())
                .edited(saved.getIsEdited())
                .repliedToId(saved.getRepliedToId())
                .replyToSenderId(replyToSenderId)
                .replyToContent(replyToContent)
                .replyToMessageType(replyToMessageType)
                .replyToHasAttachments(replyToHasAttachments)
                .replyToFileName(replyToFileName)
                .replyToFileType(replyToFileType)
                .hasAttachments(saved.getHasAttachments())
                .messageType(saved.getMessageType())
                .codeLanguage(saved.getCodeLanguage())
                .fileName(persistedFileName)
                .fileType(persistedFileType)
                .fileSize(persistedFileSize)
                .file64(persistedFile64)
                .reactions(Collections.emptyList())
                .build();
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    public AttachmentUploadResponse uploadDmAttachment(UUID uploaderId, byte[] fileBytes,
                                                       String originalFileName, String contentType) throws IOException {
        String safeFileName = sanitizeFileName(originalFileName);
        String extension = FilenameUtils.getExtension(originalFileName);
        String diskFileName = UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension);

        Path dir = Paths.get(attachmentsBaseDir, "pending");
        Files.createDirectories(dir);
        Path filePath = dir.resolve(diskFileName);
        Files.write(filePath, fileBytes);

        PendingDmAttachment saved = pendingAttachmentRepository.save(PendingDmAttachment.builder()
                .uploaderId(uploaderId)
                .filePath(filePath.toString())
                .fileName(safeFileName)
                .fileSize((long) fileBytes.length)
                .fileType(contentType != null ? contentType : "application/octet-stream")
                .uploadedAt(LocalDateTime.now())
                .build());

        return AttachmentUploadResponse.builder().attachmentId(saved.getAttachmentId()).build();
    }

    // ── History ───────────────────────────────────────────────────────────────

    public List<DmReceivedPayload> getMessagesBefore(UUID userId, UUID partnerId, LocalDateTime cursor, int limit) {
        LocalDateTime hiddenBefore = hiddenRepository.findByUserIdAndOtherUserId(userId, partnerId)
                .map(DmConversationHidden::getHiddenBefore).orElse(LocalDateTime.of(1970, 1, 1, 0, 0));
        List<DirectMessage> messages = messageRepository.findConversationBeforeCursor(
                userId, partnerId, cursor, hiddenBefore, PageRequest.of(0, limit));

        if (messages.isEmpty()) return List.of();

        List<UUID> ids = messages.stream().map(DirectMessage::getMessageId).toList();

        Map<UUID, DirectMessage> withReactions = messageRepository.findByIdsWithReactions(ids)
                .stream().collect(Collectors.toMap(DirectMessage::getMessageId, m -> m));

        Map<UUID, List<DirectMessageAttachment>> attachmentsByMessage = attachmentRepository
                .findByMessageIdIn(ids)
                .stream().collect(Collectors.groupingBy(DirectMessageAttachment::getMessageId));

        return messages.stream()
                .map(m -> toPayload(
                        withReactions.getOrDefault(m.getMessageId(), m),
                        attachmentsByMessage.getOrDefault(m.getMessageId(), Collections.emptyList())))
                .toList();
    }

    // ── Conversations ─────────────────────────────────────────────────────────

    public List<ConversationSummary> getConversations(UUID userId) {
        List<DirectMessage> latest = messageRepository.findLatestMessagePerConversation(userId);

        Map<UUID, DmConversationRead> readMap = readRepository.findAllByUserId(userId)
                .stream().collect(Collectors.toMap(DmConversationRead::getPartnerId, r -> r));

        return latest.stream().map(m -> {
            UUID partnerId = m.getSenderId().equals(userId) ? m.getRecipientId() : m.getSenderId();
            String partnerUsername = userRepository.findById(partnerId)
                    .map(u -> u.getUsername()).orElse("Unknown");
            String preview = m.getMessageType() == DirectMessage.MessageType.GIF
                    ? "GIF"
                    : m.getMessageType() == DirectMessage.MessageType.CODE
                    ? "Code snippet"
                    : (m.getHasAttachments() ? "Attachment" : truncate(m.getContent(), 60));

            boolean hasUnread;
            if (m.getSenderId().equals(userId)) {
                hasUnread = false;
            } else {
                DmConversationRead read = readMap.get(partnerId);
                hasUnread = read == null || m.getSentAt().isAfter(read.getLastReadAt());
            }

            return ConversationSummary.builder()
                    .partnerId(partnerId)
                    .partnerUsername(partnerUsername)
                    .lastMessageContent(preview)
                    .lastMessageSentAt(m.getSentAt())
                    .lastMessageType(m.getMessageType())
                    .lastMessageIsOwn(m.getSenderId().equals(userId))
                    .hasUnread(hasUnread)
                    .build();
        }).toList();
    }

    @Transactional
    public void markConversationRead(UUID userId, UUID partnerId) {
        DmConversationRead record = readRepository.findByUserIdAndPartnerId(userId, partnerId)
                .orElse(DmConversationRead.builder().userId(userId).partnerId(partnerId).build());
        record.setLastReadAt(LocalDateTime.now());
        readRepository.save(record);
    }

    // ── Edit ──────────────────────────────────────────────────────────────────

    public DirectMessage editMessage(UUID messageId, UUID requesterId, String newContent, String codeLanguage) {
        DirectMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        if (!message.getSenderId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot edit another user's message");
        }
        message.setContent(newContent);
        message.setIsEdited(true);
        if (codeLanguage != null) {
            message.setCodeLanguage(codeLanguage);
        }
        return messageRepository.save(message);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public DirectMessage deleteMessage(UUID messageId, UUID requesterId) {
        DirectMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        if (!message.getSenderId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete another user's message");
        }
        deleteAttachmentFiles(messageId);
        messageRepository.delete(message);
        return message;
    }

    @Transactional
    public void hideConversationForUser(UUID userId, UUID partnerId) {
        DmConversationHidden hidden = hiddenRepository.findByUserIdAndOtherUserId(userId, partnerId)
                .orElse(DmConversationHidden.builder().userId(userId).otherUserId(partnerId).build());
        hidden.setHiddenBefore(LocalDateTime.now());
        hiddenRepository.save(hidden);

        WsAppMessage wsMsg = new WsAppMessage(WsMessageType.DM_CONVERSATION_HIDDEN,
                DmConversationHiddenPayload.builder().partnerId(partnerId).build());
        appMessageSender.sendToUser(userId, wsMsg);
    }

    @Transactional
    public void deleteAllHistory(UUID userId, UUID partnerId) {
        List<DirectMessage> messages = messageRepository.findAllBetweenUsers(userId, partnerId);
        for (DirectMessage m : messages) {
            deleteAttachmentFiles(m.getMessageId());
        }
        messageRepository.deleteAll(messages);

        hiddenRepository.deleteByUserIdAndOtherUserId(userId, partnerId);
        hiddenRepository.deleteByUserIdAndOtherUserId(partnerId, userId);
        readRepository.deleteByUserIdAndPartnerId(userId, partnerId);
        readRepository.deleteByUserIdAndPartnerId(partnerId, userId);

        DmConversationDeletedPayload payload = DmConversationDeletedPayload.builder().partnerId(partnerId).build();
        appMessageSender.sendToUser(userId, new WsAppMessage(WsMessageType.DM_CONVERSATION_DELETED, payload));

        DmConversationDeletedPayload partnerPayload = DmConversationDeletedPayload.builder().partnerId(userId).build();
        if (appMessageSender.isOnline(partnerId)) {
            appMessageSender.sendToUser(partnerId, new WsAppMessage(WsMessageType.DM_CONVERSATION_DELETED, partnerPayload));
        }
    }

    private void deleteAttachmentFiles(UUID messageId) {
        List<DirectMessageAttachment> attachments = attachmentRepository.findByMessageId(messageId);
        for (DirectMessageAttachment att : attachments) {
            if (att.getFilePath() != null) {
                try {
                    Path file = Paths.get(att.getFilePath());
                    Files.deleteIfExists(file);
                    Path dir = file.getParent();
                    if (dir != null && Files.isDirectory(dir)) {
                        try (var entries = Files.list(dir)) {
                            if (entries.findFirst().isEmpty()) Files.deleteIfExists(dir);
                        }
                    }
                } catch (IOException e) {
                    log.warn("Could not delete attachment file {}: {}", att.getFilePath(), e.getMessage());
                }
            }
        }
        attachmentRepository.deleteAll(attachments);
    }

    // ── Reactions ─────────────────────────────────────────────────────────────

    public void addReaction(UUID messageId, UUID userId, String emoji) {
        DirectMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        DirectMessageReaction.DirectMessageReactionId id =
                new DirectMessageReaction.DirectMessageReactionId(messageId, userId, emoji);
        if (!reactionRepository.existsById(id)) {
            reactionRepository.save(DirectMessageReaction.builder().id(id).message(message).build());
        }

        UUID partnerId = message.getSenderId().equals(userId) ? message.getRecipientId() : message.getSenderId();
        appMessageSender.sendToUser(userId, new WsAppMessage(WsMessageType.DM_REACTION_ADDED,
                DmReactionAddedPayload.builder()
                        .messageId(messageId).userId(userId).emoji(emoji).conversationPartnerId(partnerId)
                        .build()));
        if (appMessageSender.isOnline(partnerId)) {
            appMessageSender.sendToUser(partnerId, new WsAppMessage(WsMessageType.DM_REACTION_ADDED,
                    DmReactionAddedPayload.builder()
                            .messageId(messageId).userId(userId).emoji(emoji).conversationPartnerId(userId)
                            .build()));
        }
    }

    public void removeReaction(UUID messageId, UUID userId, String emoji) {
        DirectMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        reactionRepository.deleteById(new DirectMessageReaction.DirectMessageReactionId(messageId, userId, emoji));

        UUID partnerId = message.getSenderId().equals(userId) ? message.getRecipientId() : message.getSenderId();
        appMessageSender.sendToUser(userId, new WsAppMessage(WsMessageType.DM_REACTION_REMOVED,
                DmReactionRemovedPayload.builder()
                        .messageId(messageId).userId(userId).emoji(emoji).conversationPartnerId(partnerId)
                        .build()));
        if (appMessageSender.isOnline(partnerId)) {
            appMessageSender.sendToUser(partnerId, new WsAppMessage(WsMessageType.DM_REACTION_REMOVED,
                    DmReactionRemovedPayload.builder()
                            .messageId(messageId).userId(userId).emoji(emoji).conversationPartnerId(userId)
                            .build()));
        }
    }

    // ── Attachment download ───────────────────────────────────────────────────

    public ResponseEntity<?> getAttachmentResource(UUID messageId, UUID requesterId) {
        DirectMessage message = messageRepository.findById(messageId).orElse(null);
        if (message == null) return ErrorResponse.of(HttpStatus.NOT_FOUND, "Message not found");

        boolean isParty = message.getSenderId().equals(requesterId) || message.getRecipientId().equals(requesterId);
        if (!isParty) return ErrorResponse.of(HttpStatus.FORBIDDEN, "Access denied");

        if (!message.getHasAttachments()) return ErrorResponse.of(HttpStatus.NOT_FOUND, "No attachment");

        List<DirectMessageAttachment> attachments = attachmentRepository.findByMessageId(messageId);
        if (attachments.isEmpty()) return ErrorResponse.of(HttpStatus.NOT_FOUND, "Attachment not found");

        DirectMessageAttachment attachment = attachments.get(0);
        Path filePath = Paths.get(attachment.getFilePath());
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "File unavailable");
        }

        try {
            byte[] bytes = Files.readAllBytes(filePath);
            String mime = attachment.getFileType() != null ? attachment.getFileType() : "application/octet-stream";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getFileName() + "\"")
                    .contentType(MediaType.parseMediaType(mime))
                    .contentLength(bytes.length)
                    .body(bytes);
        } catch (IOException e) {
            log.error("Failed to read DM attachment for message {}", messageId, e);
            return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read attachment");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public DmReceivedPayload toPayload(DirectMessage message, List<DirectMessageAttachment> attachments) {
        DmReceivedPayload.DmReceivedPayloadBuilder builder = DmReceivedPayload.builder()
                .messageId(message.getMessageId())
                .senderId(message.getSenderId())
                .recipientId(message.getRecipientId())
                .content(message.getContent())
                .sentAt(message.getSentAt())
                .edited(message.getIsEdited())
                .hasAttachments(message.getHasAttachments())
                .repliedToId(message.getRepliedToId())
                .messageType(message.getMessageType())
                .codeLanguage(message.getCodeLanguage());

        if (message.getRepliedToId() != null) {
            messageRepository.findById(message.getRepliedToId()).ifPresent(replied -> {
                builder.replyToSenderId(replied.getSenderId());
                builder.replyToContent(replied.getContent());
                builder.replyToMessageType(replied.getMessageType());
                boolean replyHasAtts = Boolean.TRUE.equals(replied.getHasAttachments());
                builder.replyToHasAttachments(replyHasAtts);
                if (replyHasAtts) {
                    List<DirectMessageAttachment> replyAtts = attachmentRepository
                            .findByMessageIdIn(List.of(replied.getMessageId()));
                    if (!replyAtts.isEmpty()) {
                        builder.replyToFileName(replyAtts.get(0).getFileName());
                        builder.replyToFileType(replyAtts.get(0).getFileType());
                    }
                }
            });
        }

        if (message.getHasAttachments() && !attachments.isEmpty()) {
            DirectMessageAttachment att = attachments.get(0);
            builder.fileName(att.getFileName())
                    .fileType(att.getFileType())
                    .fileSize(att.getFileSize());
            if (isImageType(att.getFileType())) {
                builder.file64(readFileAsBase64(att.getFilePath()));
            }
        }

        List<DmReactionAddedPayload> reactions = (message.getReactions() == null || message.getReactions().isEmpty())
                ? Collections.emptyList()
                : message.getReactions().stream()
                        .map(r -> DmReactionAddedPayload.builder()
                                .messageId(message.getMessageId())
                                .userId(r.getId().getUserId())
                                .emoji(r.getId().getEmoji())
                                .conversationPartnerId(null)
                                .build())
                        .toList();
        builder.reactions(reactions);

        return builder.build();
    }

    private boolean isImageType(String fileType) {
        return fileType != null && fileType.startsWith("image/");
    }

    private String readFileAsBase64(String storedFilePath) {
        try {
            Path p = Paths.get(storedFilePath);
            if (Files.exists(p) && Files.isReadable(p)) return Base64.getEncoder().encodeToString(Files.readAllBytes(p));
        } catch (IOException e) {
            log.error("Failed to read DM attachment file at {}", storedFilePath, e);
        }
        return null;
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
