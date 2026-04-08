package lk.customs.rms.config;

import lk.customs.rms.security.NotificationHandshakeInterceptor;
import lk.customs.rms.service.NotificationWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class NotificationWebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final NotificationHandshakeInterceptor notificationHandshakeInterceptor;

    public NotificationWebSocketConfig(NotificationWebSocketHandler notificationWebSocketHandler,
                                       NotificationHandshakeInterceptor notificationHandshakeInterceptor) {
        this.notificationWebSocketHandler = notificationWebSocketHandler;
        this.notificationHandshakeInterceptor = notificationHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, "/ws/notifications")
                .addInterceptors(notificationHandshakeInterceptor)
                .setAllowedOriginPatterns("http://localhost:*");
    }
}
