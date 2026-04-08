package lk.customs.rms.service;

import lk.customs.rms.dto.RealtimeNotificationMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final String USER_ID_ATTRIBUTE = "uid";

    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = resolveUserId(session);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        sessionsByUser
                .computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        unregister(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    public void sendToUser(Long userId, RealtimeNotificationMessage message) {
        if (userId == null || message == null) return;

        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) return;

        String payload;
        try {
            payload = toJson(message);
        } catch (Exception ignored) {
            return;
        }

        for (WebSocketSession session : new ArrayList<>(sessions)) {
            try {
                if (!session.isOpen()) {
                    unregister(session);
                    continue;
                }
                session.sendMessage(new TextMessage(payload));
            } catch (IOException ignored) {
                unregister(session);
            }
        }
    }

    public void sendToAll(RealtimeNotificationMessage message) {
        if (message == null || sessionsByUser.isEmpty()) return;

        for (Long userId : new ArrayList<>(sessionsByUser.keySet())) {
            sendToUser(userId, message);
        }
    }

    private void unregister(WebSocketSession session) {
        Long userId = resolveUserId(session);
        if (userId == null) return;

        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null) return;

        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByUser.remove(userId);
        }
    }

    private Long resolveUserId(WebSocketSession session) {
        Object value = session.getAttributes().get(USER_ID_ATTRIBUTE);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String toJson(RealtimeNotificationMessage message) {
        return "{" +
                "\"type\":\"" + escape(message.type()) + "\"," +
                "\"message\":\"" + escape(message.message()) + "\"," +
                "\"documentId\":" + numberOrNull(message.documentId()) + "," +
                "\"documentRefNo\":" + textOrNull(message.documentRefNo()) + "," +
                "\"fromUserId\":" + numberOrNull(message.fromUserId()) + "," +
                "\"fromUserName\":" + textOrNull(message.fromUserName()) + "," +
                "\"createdAt\":" + textOrNull(message.createdAt() == null ? null : message.createdAt().toString()) +
                "}";
    }

    private String textOrNull(String value) {
        if (value == null) return "null";
        return "\"" + escape(value) + "\"";
    }

    private String numberOrNull(Number value) {
        if (value == null) return "null";
        return String.valueOf(value);
    }

    private String escape(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
