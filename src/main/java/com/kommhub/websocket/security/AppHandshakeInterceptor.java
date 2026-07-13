package com.kommhub.websocket.security;

import com.kommhub.model.db.User;
import com.kommhub.security.JwtUtil;
import com.kommhub.security.CustomUserDetailsService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        try {
            String token = extractToken(request);
            if (token == null) {
                log.warn("WebSocket handshake rejected — no token");
                return false;
            }

            Claims claims = jwtUtil.validateToken(token);
            if (jwtUtil.getTokenType(claims) != JwtUtil.TokenType.ACCESS) {
                log.warn("WebSocket handshake rejected — not an access token");
                return false;
            }

            String userId = claims.get("userId", String.class);
            User user = (User) userDetailsService.loadUserById(UUID.fromString(userId));

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

            // Store in attributes — read by JwtHandshakeHandler.determineUser()
            attributes.put("principal", auth);

            log.debug("WebSocket handshake authenticated: {}", user.getUsername());
            return true;

        } catch (Exception e) {
            log.warn("WebSocket handshake rejected — {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    return param.substring(6);
                }
            }
        }
        return null;
    }
}