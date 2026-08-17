package com.kommhub.websocket;

import com.kommhub.security.CustomUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

public class WsSessionUtil {

    public static UUID getUserId(WebSocketSession session) {
        if (session.getPrincipal() instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUser().getUserId();
        }
        return null;
    }
}
