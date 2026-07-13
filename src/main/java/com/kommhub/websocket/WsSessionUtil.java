package com.kommhub.websocket;

import com.kommhub.model.db.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

public final class WsSessionUtil {

    private WsSessionUtil() {}

    public static User getUser(WebSocketSession session) {
        if (session.getPrincipal() instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    public static UUID getUserId(WebSocketSession session) {
        User user = getUser(session);
        return user != null ? user.getUserId() : null;
    }
}
