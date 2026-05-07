<template>
  <router-view />
  <ToastHost :top-offset="toastTopOffset" />
</template>

<script setup>
import { computed, onMounted, onUnmounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import ToastHost from "./components/ToastHost.vue";
import { isAuthenticated, updateSessionUser } from "./auth/currentUser";
import { getMe } from "./api/auth.api";
import { useToast } from "./composables/useToast";
import {
  connectRealtimeNotifications,
  disconnectRealtimeNotifications,
  subscribeRealtimeNotifications,
} from "./services/realtimeNotifications";
import {
  getDocumentNotificationMessage,
  isDocumentNotification,
} from "./utils/realtimeNotificationLogic";

const route = useRoute();
const router = useRouter();
const { info, infoWithAction } = useToast();

let unsubscribeRealtime = null;

const toastTopOffset = computed(() => (route.path === "/login" ? 16 : 72));

let permissionRefreshInFlight = false;

async function refreshPermissionsFromServer() {
  if (!isAuthenticated() || permissionRefreshInFlight) return;
  permissionRefreshInFlight = true;
  try {
    const me = await getMe();
    updateSessionUser({
      id: me?.id,
      username: me?.username,
      fullName: me?.fullName,
      role: me?.role,
      permissions: me?.permissions || [],
      name: me?.fullName,
      hasProfilePicture: !!me?.hasProfilePicture,
      profilePictureUpdatedAt: me?.profilePictureUpdatedAt || null,
    });
    window.dispatchEvent(new CustomEvent("rms_permissions_updated", { detail: me }));
  } catch {
    // Ignore refresh failures; existing session continues.
  } finally {
    permissionRefreshInFlight = false;
  }
}

function emitNotificationTrace(stage, payload, targetPath) {
  window.dispatchEvent(new CustomEvent("rms_notification_trace", {
    detail: {
      stage,
      documentId: payload?.documentId ?? null,
      targetPath: targetPath || null,
      at: Date.now(),
    },
  }));
}

function showBrowserNotification(payload) {
  if (!("Notification" in window)) return;
  if (Notification.permission !== "granted") return;

  const title = "New document received";
  const body = payload?.message || "A new document has been assigned to you.";
  const notification = new Notification(title, { body });
  const targetPath = payload?.documentId ? `/documents/${payload.documentId}` : "/inbox";

  emitNotificationTrace("shown", payload, targetPath);

  notification.onclick = () => {
    emitNotificationTrace("clicked", payload, targetPath);

    router.push(targetPath)
      .then(() => {
        emitNotificationTrace("navigated", payload, targetPath);
      })
      .catch(() => {
        emitNotificationTrace("fallback_location_assign", payload, targetPath);
        window.location.assign(targetPath);
      });

    notification.close();
    window.focus();
  };
}

function handleRealtimeMessage(payload) {
  if (!payload) return;

  if (payload.type === "PERMISSIONS_UPDATED") {
    refreshPermissionsFromServer();
    info("Permissions updated. Access controls refreshed.", 2800);
    return;
  }

  if (!isDocumentNotification(payload)) return;

  const message = getDocumentNotificationMessage(payload);
  if (payload.documentId) {
    infoWithAction(message, `/documents/${payload.documentId}`, "Open document", 5500);
  } else {
    info(message, 4500);
  }
  showBrowserNotification(payload);
  window.dispatchEvent(new CustomEvent("rms_document_received", { detail: payload }));
}

function handleAuthChanged() {
  if (!isAuthenticated()) {
    if (unsubscribeRealtime) {
      unsubscribeRealtime();
      unsubscribeRealtime = null;
    }
    disconnectRealtimeNotifications();
    return;
  }

  if (!unsubscribeRealtime) {
    unsubscribeRealtime = subscribeRealtimeNotifications(handleRealtimeMessage);
  }

  connectRealtimeNotifications();

  if ("Notification" in window && Notification.permission === "default") {
    Notification.requestPermission().catch(() => {});
  }
}

onMounted(() => {
  window.addEventListener("rms_auth_changed", handleAuthChanged);
  handleAuthChanged();
});

onUnmounted(() => {
  window.removeEventListener("rms_auth_changed", handleAuthChanged);
  if (unsubscribeRealtime) {
    unsubscribeRealtime();
    unsubscribeRealtime = null;
  }
  disconnectRealtimeNotifications();
});
</script>
