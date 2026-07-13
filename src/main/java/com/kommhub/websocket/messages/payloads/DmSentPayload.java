package com.kommhub.websocket.messages.payloads;

import com.kommhub.model.db.DirectMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DmSentPayload {

    private UUID recipientId;
    private String content;
    private UUID repliedToId;
    private boolean hasAttachments;
    private UUID attachmentId;
    private DirectMessage.MessageType messageType;
    private String codeLanguage;
}
