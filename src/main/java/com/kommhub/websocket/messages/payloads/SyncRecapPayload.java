package com.kommhub.websocket.messages.payloads;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyncRecapPayload {

    private List<ServerPayload> serversList;
    private List<ServerMemberPayload> membersList;
    /** Servers the installation should purge (deletion requested while it may have been offline). */
    private List<UUID> pendingDeletionServerIds;
}
