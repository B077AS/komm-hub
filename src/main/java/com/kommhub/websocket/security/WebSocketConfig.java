package com.kommhub.websocket.security;

import com.kommhub.websocket.managers.AppSessionsManager;
import com.kommhub.websocket.managers.InstallationSessionsManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AppSessionsManager appSessionsManager;
    private final AppHandshakeInterceptor authInterceptor;
    private final InstallationHandshakeInterceptor installationHandshakeInterceptor;
    private final JwtHandshakeHandler handshakeHandler;
    private final InstallationSessionsManager installationSessionsManager;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(appSessionsManager, "/ws")
                .addInterceptors(authInterceptor)
                .setHandshakeHandler(handshakeHandler)
                .setAllowedOriginPatterns("*");

        registry.addHandler(installationSessionsManager, "/ws/installations")
                .addInterceptors(installationHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(15 * 1024 * 1024); // 15 MB — covers 10 MB file + base64 overhead
        return container;
    }
}