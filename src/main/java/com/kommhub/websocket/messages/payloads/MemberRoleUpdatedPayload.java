package com.kommhub.websocket.messages.payloads;

import lombok.Data;

import java.util.UUID;

@Data
public class MemberRoleUpdatedPayload {
    private UUID serverId;
    private UUID userId;
    private String newRole;
}
