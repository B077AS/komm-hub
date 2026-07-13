package com.kommhub.websocket.messages;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WsInstallationMessage {

    private WsMessageType type;
    private Object payload;
}