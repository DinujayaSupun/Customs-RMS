<template>
  <div class="app">
    <header class="header">
      <div class="brand">
        <span class="brand-name">Sri Lanka Customs</span>
        <span class="brand-sub">Document Workflow Management System</span>
      </div>

      <div class="user">
        <div class="avatarWrap">
          <img
            v-if="avatarUrl && !avatarBroken"
            :src="avatarUrl"
            class="avatar"
            alt="Profile picture"
            @error="avatarBroken = true"
          />
          <div v-else class="avatar avatarFallback">{{ initials }}</div>
        </div>
        <span class="user-role">
          {{ currentUser?.fullName || currentUser?.name }} • {{ currentUser?.role }} • ID {{ currentUser?.id }}
        </span>
        <button class="logout" type="button" @click="logout">Logout</button>
      </div>
    </header>

    <div class="body">
      <aside class="sidebar">
        <div class="sidebar-title">Navigation</div>

        <router-link to="/inbox" class="nav">My Inbox</router-link>
        <router-link to="/documents" class="nav">Documents</router-link>
        <router-link to="/profile" class="nav">My Profile</router-link>
        <router-link v-if="canViewLogs" to="/logs" class="nav">Logs</router-link>
        <router-link v-if="currentUser?.role === 'ADMIN'" to="/users" class="nav">Users</router-link>
        <router-link v-if="currentUser?.role === 'ADMIN'" to="/permissions" class="nav">Permissions</router-link>
      </aside>

      <main class="content">
        <slot />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { getMyWorkloadStats } from "../api/documents.api";
import { buildMyProfilePictureUrl } from "../api/auth.api";
import { clearSession, getCurrentUser, hasPermission } from "../auth/currentUser";
import { useToast } from "../composables/useToast";

const router = useRouter();
const userRef = ref(getCurrentUser());
const avatarBroken = ref(false);
const { success, info } = useToast();

const currentUser = computed(() => userRef.value);
const canViewLogs = computed(() => hasPermission(currentUser.value, "VIEW_LOGS"));

const initials = computed(() => {
  const text = String(currentUser.value?.fullName || currentUser.value?.username || "U").trim();
  if (!text) return "U";
  const parts = text.split(/\s+/).slice(0, 2);
  return parts.map((p) => p[0]?.toUpperCase() || "").join("") || "U";
});

const avatarUrl = computed(() => {
  if (!currentUser.value?.hasProfilePicture) return "";
  return buildMyProfilePictureUrl(currentUser.value?.profilePictureUpdatedAt || Date.now());
});

function onAuthChanged() {
  userRef.value = getCurrentUser();
  avatarBroken.value = false;
}

function logout() {
  clearSession();
  router.replace("/login");
}

function greetingByTime() {
  const hour = new Date().getHours();
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}

async function getMyDocumentStats() {
  const data = await getMyWorkloadStats();
  return {
    assignedCount: Number(data?.assignedCount || 0),
    unopenedCount: Number(data?.unopenedCount || 0),
  };
}

async function showPendingWelcome() {
  let raw = null;
  try {
    raw = window.sessionStorage.getItem("rms_pending_welcome");
    if (!raw) return;

    const payload = JSON.parse(raw);
    const fullName = String(payload?.fullName || currentUser.value?.fullName || currentUser.value?.username || "User").trim();
    const role = String(payload?.role || currentUser.value?.role || "USER").trim();
    const stats = await getMyDocumentStats();

    const primaryMessage = `${greetingByTime()}, ${fullName}. You are signed in as ${role}.`;
    success(primaryMessage, 4200);

    const secondaryMessage = `You have ${stats.unopenedCount} unopened documents out of ${stats.assignedCount} assigned to you.`;
    window.setTimeout(() => {
      info(secondaryMessage, 9000);
    }, 320);
  } catch {
    // Ignore malformed payloads and continue normal page render.
  } finally {
    if (raw !== null) {
      window.sessionStorage.removeItem("rms_pending_welcome");
    }
  }
}

onMounted(() => {
  window.addEventListener("rms_auth_changed", onAuthChanged);
  showPendingWelcome();
});

onUnmounted(() => {
  window.removeEventListener("rms_auth_changed", onAuthChanged);
});
</script>

<style scoped>
.app { font-family: Arial, sans-serif; height: 100vh; display: flex; flex-direction: column; }

.header {
  height: 60px;
  background: #1f2937;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
}

.brand { display: flex; flex-direction: column; line-height: 1.1; }
.brand-name { font-weight: 800; font-size: 14px; }
.brand-sub { font-size: 12px; opacity: 0.9; }

.user { display: flex; align-items: center; gap: 12px; }
.user-role { font-size: 12px; opacity: 0.95; }

.avatarWrap { display: flex; align-items: center; }
.avatar {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  object-fit: cover;
  border: 1px solid rgba(255,255,255,0.4);
}

.avatarFallback {
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
  background: #475569;
  color: #fff;
}

.logout {
  background: transparent;
  border: 1px solid rgba(255,255,255,0.5);
  color: #fff;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
}

.body { flex: 1; display: flex; min-height: 0; }

.sidebar {
  width: 240px;
  background: #111827;
  color: #fff;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.sidebar-title { font-weight: 800; margin-bottom: 10px; opacity: 0.95; }

.nav {
  color: #d1d5db;
  text-decoration: none;
  padding: 10px 12px;
  border-radius: 8px;
}
.nav:hover { background: #374151; }
.nav.router-link-active {
  background: #2563eb;
  color: #fff;
  font-weight: 700;
}

.content { flex: 1; background: #f3f4f6; padding: 20px; overflow-y: auto; }
</style>