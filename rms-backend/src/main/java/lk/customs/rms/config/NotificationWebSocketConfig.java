package lk.customs.rms.config;

import lk.customs.rms.security.NotificationHandshakeInterceptor;
import lk.customs.rms.websocket.NotificationWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSocket
public class NotificationWebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final NotificationHandshakeInterceptor notificationHandshakeInterceptor;

    // Reuse the same configurable origin list as HTTP CORS so production domains are allowed without code changes.
    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    public NotificationWebSocketConfig(NotificationWebSocketHandler notificationWebSocketHandler,
                                       NotificationHandshakeInterceptor notificationHandshakeInterceptor) {
        this.notificationWebSocketHandler = notificationWebSocketHandler;
        this.notificationHandshakeInterceptor = notificationHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        registry.addHandler(notificationWebSocketHandler, "/ws/notifications")
                .addInterceptors(notificationHandshakeInterceptor)
                .setAllowedOriginPatterns(origins.toArray(String[]::new));
    }
}
