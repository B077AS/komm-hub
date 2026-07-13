package com.kommhub.websocket.messages.payloads;

import com.kommhub.model.db.DirectMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DmReceivedPayload {

    private UUID messageId;
    private UUID senderId;
    private UUID recipientId;
    private String content;
    private LocalDateTime sentAt;
    private boolean edited;
    private UUID repliedToId;
    private UUID replyToSenderId;
    private String replyToContent;
    private boolean hasAttachments;
    private String fileName;
    private String fileType;
    private String file64;
    private long fileSize;
    private List<DmReactionAddedPayload> reactions;
    private DirectMessage.MessageType messageType;
    private String codeLanguage;
    private DirectMessage.MessageType replyToMessageType;
    private boolean replyToHasAttachments;
    private String replyToFileName;
    private String replyToFileType;
}
