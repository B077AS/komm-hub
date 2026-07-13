package com.kommhub.websocket.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kommhub.model.db.ServerMember;
import com.kommhub.repository.ServerMemberRepository;
import com.kommhub.websocket.interfaces.InstallationInboundMessageHandler;
import com.kommhub.websocket.messages.WsMessageType;
import com.kommhub.websocket.messages.payloads.MemberRoleUpdatedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberRoleUpdatedHandler implements InstallationInboundMessageHandler {

    private final Gson gson;
    private final ServerMemberRepository serverMemberRepository;

    @Override
    public WsMessageType getType() {
        return WsMessageType.MEMBER_ROLE_UPDATED;
    }

    @Override
    public void handle(WebSocketSession session, JsonObject payload) {
        MemberRoleUpdatedPayload p = gson.fromJson(payload, MemberRoleUpdatedPayload.class);
        serverMemberRepository.findByUserIdAndServerId(p.getUserId(), p.getServerId())
                .ifPresent(member -> {
                    member.setRole(ServerMember.Role.valueOf(p.getNewRole()));
                    serverMemberRepository.save(member);
                    log.info("Updated hub role for userId={} in serverId={} to {}", p.getUserId(), p.getServerId(), p.getNewRole());
                });
    }
}
