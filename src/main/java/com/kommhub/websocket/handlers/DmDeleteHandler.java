package com.kommhub.websocket.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kommhub.model.db.DirectMessage;
import com.kommhub.service.DirectMessageService;
import com.kommhub.websocket.WsSessionUtil;
import com.kommhub.websocket.interfaces.AppInboundMessageHandler;
import com.kommhub.websocket.senders.AppMessageSender;
import com.kommhub.websocket.messages.WsAppMessage;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.DmDeletePayload;
import com.kommhub.websocket.messages.payloads.DmDeletedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DmDeleteHandler implements AppInboundMessageHandler {

    private final Gson gson;
    private final DirectMessageService directMessageService;
    private final AppMessageSender appMessageSender;

    @Override
    public WsMessageType getType() {
        return WsMessageType.DM_DELETE;
    }

    @Override
    public void handle(WebSocketSession session, JsonObject payload) {
        UUID senderId = WsSessionUtil.getUserId(session);
        if (senderId == null) return;

        DmDeletePayload req = gson.fromJson(payload, DmDeletePayload.class);
        if (req.getMessageId() == null) return;

        DirectMessage deleted;
        try {
            deleted = directMessageService.deleteMessage(req.getMessageId(), senderId);
        } catch (Exception e) {
            log.warn("DM_DELETE denied for user={}: {}", senderId, e.getMessage());
            return;
        }

        UUID partnerId = deleted.getSenderId().equals(senderId)
                ? deleted.getRecipientId() : deleted.getSenderId();

        appMessageSender.sendToUser(senderId, new WsAppMessage(WsMessageType.DM_DELETED,
                DmDeletedPayload.builder()
                        .messageId(deleted.getMessageId())
                        .conversationPartnerId(partnerId)
                        .build()));
        if (appMessageSender.isOnline(partnerId)) {
            appMessageSender.sendToUser(partnerId, new WsAppMessage(WsMessageType.DM_DELETED,
                    DmDeletedPayload.builder()
                            .messageId(deleted.getMessageId())
                            .conversationPartnerId(senderId)
                            .build()));
        }
    }
}
