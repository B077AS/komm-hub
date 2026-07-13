package com.kommhub.websocket.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kommhub.model.db.User;
import com.kommhub.websocket.WsSessionUtil;
import com.kommhub.websocket.interfaces.AppInboundMessageHandler;
import com.kommhub.websocket.senders.AppMessageSender;
import com.kommhub.websocket.messages.WsAppMessage;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.DmTypingPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class DmTypingHandler implements AppInboundMessageHandler {

    private final Gson gson;
    private final AppMessageSender appMessageSender;

    @Override
    public WsMessageType getType() {
        return WsMessageType.DM_TYPING;
    }

    @Override
    public void handle(WebSocketSession session, JsonObject payload) {
        User sender = WsSessionUtil.getUser(session);
        if (sender == null) return;

        DmTypingPayload req = gson.fromJson(payload, DmTypingPayload.class);
        if (req.getRecipientId() == null) return;

        if (!appMessageSender.isOnline(req.getRecipientId())) return;

        DmTypingPayload forward = DmTypingPayload.builder()
                .recipientId(req.getRecipientId())
                .senderId(sender.getUserId())
                .build();
        appMessageSender.sendToUser(req.getRecipientId(),
                new WsAppMessage(WsMessageType.DM_TYPING, forward));
    }
}
