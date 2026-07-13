package com.kommhub.websocket.messages.payloads;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Installation → hub: a server has been scheduled for deletion (hub drops its rows and notifies
 * members). Also reused hub → app to tell each member's client to remove the server from its list.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServerDeletedPayload {
    private UUID serverId;
}
