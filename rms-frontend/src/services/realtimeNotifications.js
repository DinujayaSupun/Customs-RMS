import { getAccessToken, isAuthenticated } from "../auth/currentUser";

const listeners = new Set();
let socket = null;
let reconnectTimer = null;
let manuallyClosed = false;

function clearReconnectTimer() {
  if (reconnectTimer) {
    window.clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
}

function scheduleReconnect() {
  if (manuallyClosed) return;
  clearReconnectTimer();
  reconnectTimer = window.setTimeout(() => {
    connectRealtimeNotifications();
  }, 3000);
}

function toWebSocketBaseUrl(httpBase) {
  if (httpBase.startsWith("https://")) return httpBase.replace("https://", "wss://");
  if (httpBase.startsWith("http://")) return httpBase.replace("http://", "ws://");
  return `ws://${httpBase}`;
}

function resolveSocketUrl() {
  const token = getAccessToken();
  if (!token) return null;

  const apiBase = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
  const wsBase = toWebSocketBaseUrl(apiBase);
  return `${wsBase}/ws/notifications?token=${encodeURIComponent(token)}`;
}

export function connectRealtimeNotifications() {
  if (!isAuthenticated()) return;

  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
    return;
  }

  const socketUrl = resolveSocketUrl();
  if (!socketUrl) return;

  manuallyClosed = false;
  clearReconnectTimer();

  socket = new WebSocket(socketUrl);

  socket.onmessage = (event) => {
    try {
      const payload = JSON.parse(event.data);
      for (const listener of listeners) {
        try {
          listener(payload);
        } catch {
          // Keep other listeners working even if one throws.
        }
      }
    } catch {
      // Ignore non-JSON messages.
    }
  };

  socket.onclose = () => {
    socket = null;
    scheduleReconnect();
  };

  socket.onerror = () => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.close();
    }
  };
}

export function disconnectRealtimeNotifications() {
  manuallyClosed = true;
  clearReconnectTimer();

  if (!socket) return;

  try {
    socket.close();
  } catch {
    // Ignore close failures.
  }

  socket = null;
}

export function subscribeRealtimeNotifications(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
