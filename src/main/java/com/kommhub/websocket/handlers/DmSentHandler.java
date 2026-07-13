package com.kommhub.websocket.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kommhub.model.db.DirectMessage;
import com.kommhub.model.db.User;
import com.kommhub.repository.FriendRepository;
import com.kommhub.repository.UserRepository;
import com.kommhub.service.DirectMessageService;
import com.kommhub.websocket.WsSessionUtil;
import com.kommhub.websocket.interfaces.AppInboundMessageHandler;
import com.kommhub.websocket.senders.AppMessageSender;
import com.kommhub.websocket.messages.WsAppMessage;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.DmReceivedPayload;
import com.kommhub.websocket.messages.payloads.DmSendRejectedPayload;
import com.kommhub.websocket.messages.payloads.DmSentPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
@RequiredArgsConstructor
public class DmSentHandler implements AppInboundMessageHandler {

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_CODE_MESSAGE_LENGTH = 50000;

    private final Gson gson;
    private final DirectMessageService directMessageService;
    private final AppMessageSender appMessageSender;
    private final FriendRepository friendRepository;
    private final UserRepository userRepository;

    @Override
    public WsMessageType getType() {
        return WsMessageType.DM_SENT;
    }

    @Override
    public void handle(WebSocketSession session, JsonObject payload) {
        User sender = WsSessionUtil.getUser(session);
        if (sender == null) return;

        DmSentPayload sent = gson.fromJson(payload, DmSentPayload.class);
        if (sent.getRecipientId() == null) {
            log.warn("DM_SENT denied: missing recipientId (senderId={})", sender.getUserId());
            return;
        }

        if (sender.getUserId().equals(sent.getRecipientId())) {
            log.warn("DM_SENT denied: user {} attempted to message themselves", sender.getUserId());
            return;
        }

        User recipient = userRepository.findById(sent.getRecipientId()).orElse(null);
        if (recipient == null) {
            log.warn("DM_SENT denied: recipient {} not found (senderId={})", sent.getRecipientId(), sender.getUserId());
            return;
        }

        if (!isDeliveryAllowed(sender, recipient)) {
            log.info("DM_SENT rejected by privacy ({}): {} → {}",
                    recipient.getDmPrivacy(), sender.getUserId(), recipient.getUserId());
            appMessageSender.sendToUser(sender.getUserId(), new WsAppMessage(
                    WsMessageType.DM_SEND_REJECTED, buildRejection(recipient)));
            return;
        }

        int maxLength = sent.getMessageType() == DirectMessage.MessageType.CODE
                ? MAX_CODE_MESSAGE_LENGTH : MAX_MESSAGE_LENGTH;
        if (sent.getContent() != null && sent.getContent().length() > maxLength) {
            log.warn("DM_SENT denied: content exceeds {} chars (senderId={})", maxLength, sender.getUserId());
            return;
        }

        DmReceivedPayload received = directMessageService.save(sender.getUserId(), sent);
        log.info("DM saved: {} → {}, messageId={}", sender.getUserId(), sent.getRecipientId(), received.getMessageId());

        WsAppMessage receivedMsg = new WsAppMessage(WsMessageType.DM_RECEIVED, received);

        appMessageSender.sendToUser(sender.getUserId(), receivedMsg);
        if (appMessageSender.isOnline(sent.getRecipientId()))
            appMessageSender.sendToUser(sent.getRecipientId(), receivedMsg);
    }

    private boolean isDeliveryAllowed(User sender, User recipient) {
        User.DmPrivacy privacy = recipient.getDmPrivacy() != null
                ? recipient.getDmPrivacy() : User.DmPrivacy.EVERYONE;
        return switch (privacy) {
            case EVERYONE -> true;
            case FRIENDS_ONLY -> friendRepository.areFriends(sender.getUserId(), recipient.getUserId());
            case NONE -> false;
        };
    }

    private DmSendRejectedPayload buildRejection(User recipient) {
        User.DmPrivacy privacy = recipient.getDmPrivacy() != null
                ? recipient.getDmPrivacy() : User.DmPrivacy.EVERYONE;
        String name = recipient.getUsername();
        String description = switch (privacy) {
            case FRIENDS_ONLY -> name + " only accepts direct messages from friends.";
            case NONE -> name + " isn't accepting direct messages right now.";
            case EVERYONE -> "Your message could not be delivered.";
        };
        return DmSendRejectedPayload.builder()
                .recipientId(recipient.getUserId())
                .title("Message not delivered")
                .description(description)
                .build();
    }
}
