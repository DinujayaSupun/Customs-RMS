package lk.customs.rms.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class NotificationHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    public NotificationHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            setUnauthorized(response);
            return false;
        }

        // Browsers cannot set custom headers for WebSocket handshakes, so the frontend passes JWT as a query parameter.
        String token = servletRequest.getServletRequest().getParameter("token");
        if (token == null || token.isBlank()) {
            setUnauthorized(response);
            return false;
        }

        try {
            String trimmedToken = token.trim();
            // A scoped download token (short-lived, carried in URLs) must never authenticate a
            // WebSocket session either - the same rule already enforced for the HTTP Bearer path.
            if (jwtService.isDownloadToken(trimmedToken)) {
                setUnauthorized(response);
                return false;
            }

            Long userId = jwtService.extractUserId(trimmedToken);
            String username = jwtService.extractUsername(trimmedToken);
            if (userId == null || username == null || username.isBlank()) {
                setUnauthorized(response);
                return false;
            }

            // The handler uses these attributes to route messages to all sessions for the authenticated user.
            attributes.put("uid", userId);
            attributes.put("username", username);
            return true;
        } catch (Exception ex) {
            setUnauthorized(response);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // No-op
    }

    private void setUnauthorized(ServerHttpResponse response) {
        if (response instanceof ServletServerHttpResponse servletResponse) {
            servletResponse.getServletResponse().setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
