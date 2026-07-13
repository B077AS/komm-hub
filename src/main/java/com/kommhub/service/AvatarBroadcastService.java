package com.kommhub.service;

import com.kommhub.model.db.Friend;
import com.kommhub.model.db.Friend.FriendStatus;
import com.kommhub.model.db.ServerMember;
import com.kommhub.model.db.User;
import com.kommhub.repository.FriendRepository;
import com.kommhub.repository.ServerMemberRepository;
import com.kommhub.websocket.messages.WsAppMessage;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.AvatarUpdatedPayload;
import com.kommhub.websocket.senders.AppMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Notifies the people who could plausibly have a user's avatar cached — their
 * co-server-members and accepted friends — that the avatar changed, so each
 * client can evict its cached copy and refetch lazily on the next render.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarBroadcastService {

    private final ServerMemberRepository serverMemberRepository;
    private final FriendRepository friendRepository;
    private final AppMessageSender appMessageSender;

    public void broadcastAvatarUpdate(User user) {
        UUID userId = user.getUserId();
        Set<UUID> recipients = new HashSet<>();

        // Co-members: anyone sharing a server with this user
        for (ServerMember membership : serverMemberRepository.findByUserId(userId)) {
            for (ServerMember other : serverMemberRepository.findByServerId(membership.getServerId())) {
                if (!other.getUserId().equals(userId)) {
                    recipients.add(other.getUserId());
                }
            }
        }

        // Accepted friends (either direction) — they see the avatar in DMs / friends list
        for (Friend f : friendRepository.findByRequesterAndStatus(user, FriendStatus.ACCEPTED)) {
            recipients.add(f.getAddressee().getUserId());
        }
        for (Friend f : friendRepository.findByAddresseeAndStatus(user, FriendStatus.ACCEPTED)) {
            recipients.add(f.getRequester().getUserId());
        }

        recipients.remove(userId);
        if (recipients.isEmpty()) return;

        WsAppMessage message = new WsAppMessage(
                WsMessageType.USER_AVATAR_UPDATED,
                AvatarUpdatedPayload.builder().userId(userId).build()
        );
        recipients.stream()
                .filter(appMessageSender::isOnline)
                .forEach(id -> appMessageSender.sendToUser(id, message));

        log.debug("Broadcast avatar update to {} recipients for userId={}", recipients.size(), userId);
    }
}
