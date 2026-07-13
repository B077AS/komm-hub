package com.kommhub.service;

import com.kommhub.model.db.ServerMember;
import com.kommhub.model.db.User;
import com.kommhub.repository.ServerMemberRepository;
import com.kommhub.websocket.messages.WsAppMessage;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.MemberStatusUpdatedPayload;
import com.kommhub.websocket.senders.AppMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private final ServerMemberRepository serverMemberRepository;
    private final AppMessageSender appMessageSender;

    public void broadcastStatusToCoMembers(UUID userId, User.UserStatus status) {
        Set<UUID> coMemberIds = new HashSet<>();
        for (ServerMember membership : serverMemberRepository.findByUserId(userId)) {
            for (ServerMember other : serverMemberRepository.findByServerId(membership.getServerId())) {
                if (!other.getUserId().equals(userId)) {
                    coMemberIds.add(other.getUserId());
                }
            }
        }

        if (coMemberIds.isEmpty()) return;

        WsAppMessage message = new WsAppMessage(
                WsMessageType.MEMBER_STATUS_UPDATED,
                MemberStatusUpdatedPayload.builder().userId(userId).status(status).build()
        );
        coMemberIds.stream()
                .filter(appMessageSender::isOnline)
                .forEach(id -> appMessageSender.sendToUser(id, message));

        log.debug("Broadcast {} status to {} co-members for userId={}", status, coMemberIds.size(), userId);
    }
}
