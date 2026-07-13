package com.kommhub.websocket.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kommhub.model.db.DirectMessage;
import com.kommhub.model.db.User;
import com.kommhub.service.DirectMessageService;
import com.kommhub.websocket.WsSessionUtil;
import com.kommhub.websocket.interfaces.AppInboundMessageHandler;
import com.kommhub.websocket.senders.AppMessageSender;
import com.kommhub.websocket.messages.WsAppMessage;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.DmEditPayload;
import com.kommhub.websocket.messages.payloads.DmEditedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DmEditHandler implements AppInboundMessageHandler {

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_CODE_MESSAGE_LENGTH = 50000;

    private final Gson gson;
    private final DirectMessageService directMessageService;
    private final AppMessageSender appMessageSender;

    @Override
    public WsMessageType getType() {
        return WsMessageType.DM_EDIT;
    }

    @Override
    public void handle(WebSocketSession session, JsonObject payload) {
        User sender = WsSessionUtil.getUser(session);
        if (sender == null) return;

        DmEditPayload req = gson.fromJson(payload, DmEditPayload.class);
        if (req.getMessageId() == null || req.getContent() == null) return;

        int maxLength = req.getCodeLanguage() != null ? MAX_CODE_MESSAGE_LENGTH : MAX_MESSAGE_LENGTH;
        if (req.getContent().length() > maxLength) {
            log.warn("DM_EDIT denied: content exceeds {} chars (senderId={})", maxLength, sender.getUserId());
            return;
        }

        DirectMessage edited;
        try {
            edited = directMessageService.editMessage(req.getMessageId(), sender.getUserId(), req.getContent(), req.getCodeLanguage());
        } catch (Exception e) {
            log.warn("DM_EDIT denied for user={}: {}", sender.getUserId(), e.getMessage());
            return;
        }

        UUID partnerId = edited.getSenderId().equals(sender.getUserId())
                ? edited.getRecipientId() : edited.getSenderId();

        appMessageSender.sendToUser(sender.getUserId(), new WsAppMessage(WsMessageType.DM_EDITED,
                DmEditedPayload.builder()
                        .messageId(edited.getMessageId())
                        .content(edited.getContent())
                        .codeLanguage(edited.getCodeLanguage())
                        .conversationPartnerId(partnerId)
                        .build()));
        if (appMessageSender.isOnline(partnerId)) {
            appMessageSender.sendToUser(partnerId, new WsAppMessage(WsMessageType.DM_EDITED,
                    DmEditedPayload.builder()
                            .messageId(edited.getMessageId())
                            .content(edited.getContent())
                            .codeLanguage(edited.getCodeLanguage())
                            .conversationPartnerId(sender.getUserId())
                            .build()));
        }
    }
}
