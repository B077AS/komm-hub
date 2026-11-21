package com.kommhub.websocket.messages.payloads;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The address players are given for an installation (its public address, else the IP the hub sees it connect from),
 * sent every time it connects so a changed IP reaches it. The installation hands it to its media server, which
 * advertises it for voice.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AddressUpdatePayload {

    private String publicAddress;
}
