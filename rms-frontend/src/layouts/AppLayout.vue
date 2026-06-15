<template>
  <div class="app">
    <header class="header">
      <div class="brand">
        <span class="brand-logo">
          <img src="/sri-lanka-customs-logo.svg" alt="Sri Lanka Customs logo" />
        </span>
        <span class="brand-copy">
          <span class="brand-name">Sri Lanka Customs</span>
          <span class="brand-sub">Document Workflow Management System</span>
        </span>
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
          {{ currentUserLabel }}
        </span>
        <button class="logout" type="button" @click="logout">Logout</button>
      </div>
    </header>

    <div v-if="backendUnavailable" class="serverBanner" role="status">
      <span>Cannot connect to server. Some data may be unavailable.</span>
      <button type="button" class="serverBannerAction" @click="dismissBackendBanner">Dismiss</button>
    </div>

    <div class="body">
      <aside
        class="sidebar"
        :class="{ 'sidebar-expanded': isSidebarExpanded }"
        ref="sidebarRef"
        @mouseenter="expandSidebar"
        @mouseleave="scheduleSidebarCollapse"
        @focusin="expandSidebar"
        @focusout="handleSidebarFocusOut"
      >
        <div class="sidebar-top">
          <div class="sidebar-mark" aria-label="Navigation menu">
            <svg viewBox="0 0 48 48" class="sidebar-mark-svg" aria-hidden="true">
              <path d="M14 17h20" />
              <path d="M14 24h20" />
              <path d="M14 31h20" />
            </svg>
          </div>
          <div class="sidebar-text sidebar-heading">Navigation</div>
        </div>

        <nav class="navList">
          <router-link
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            class="nav"
            :title="item.label"
            @pointerdown="handleSidebarNavPress"
            @click="handleSidebarNavClick"
          >
            <span class="nav-icon" :class="`nav-icon-${item.icon}`" aria-hidden="true">
              <svg v-if="item.icon === 'inbox'" viewBox="0 0 24 24" class="nav-svg">
                <path d="M4 7h16v8h-4l-2 3h-4l-2-3H4z" />
                <path d="M4 7l2-3h12l2 3" />
              </svg>
              <svg v-else-if="item.icon === 'documents'" viewBox="0 0 24 24" class="nav-svg">
                <path d="M7 3h7l5 5v13H7z" />
                <path d="M14 3v5h5" />
                <path d="M9 13h6M9 17h6" />
              </svg>
              <svg v-else-if="item.icon === 'profile'" viewBox="0 0 24 24" class="nav-svg">
                <circle cx="12" cy="8" r="3.5" />
                <path d="M5 19c1.8-3 4.3-4.5 7-4.5s5.2 1.5 7 4.5" />
              </svg>
              <svg v-else-if="item.icon === 'logs'" viewBox="0 0 24 24" class="nav-svg">
                <path d="M6 18V9M12 18V6M18 18v-4" />
                <path d="M4 20h16" />
              </svg>
              <svg v-else-if="item.icon === 'users'" viewBox="0 0 24 24" class="nav-svg">
                <circle cx="9" cy="9" r="3" />
                <circle cx="17" cy="10" r="2.5" />
                <path d="M4.5 19c1.3-2.7 3.4-4 6-4s4.7 1.3 6 4" />
                <path d="M14.5 18c.8-1.8 2.2-2.8 4-3" />
              </svg>
              <svg v-else-if="item.icon === 'permissions'" viewBox="0 0 24 24" class="nav-svg">
                <circle cx="12" cy="12" r="3.2" />
                <path d="M12 4.5v2.2M12 17.3v2.2M19.5 12h-2.2M6.7 12H4.5M17.3 6.7l-1.6 1.6M8.3 15.7l-1.6 1.6M17.3 17.3l-1.6-1.6M8.3 8.3L6.7 6.7" />
              </svg>
            </span>
            <span class="sidebar-text nav-label">{{ item.label }}</span>
          </router-link>
        </nav>
      </aside>

      <main class="content">
        <slot />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { getMyWorkloadStats } from "../api/documents.api";
import { createMyProfilePictureUrl } from "../api/auth.api";
import { clearSession, getCurrentUser, hasPermission } from "../auth/currentUser";
import { formatUserLabel } from "../auth/userLabel";
import { useToast } from "../composables/useToast";
import { getBackendStatus } from "../services/backendStatus";

const SIDEBAR_EXPANDED_KEY = "rms_sidebar_expanded";
let persistedSidebarExpanded = false;

function readPersistedSidebarExpanded() {
  try {
    return window.sessionStorage.getItem(SIDEBAR_EXPANDED_KEY) === "true";
  } catch {
    return persistedSidebarExpanded;
  }
}

function setPersistedSidebarExpanded(value) {
  persistedSidebarExpanded = value;
  try {
    window.sessionStorage.setItem(SIDEBAR_EXPANDED_KEY, value ? "true" : "false");
  } catch {
    // Session storage can be blocked; the module-level value still covers normal navigation.
  }
}

const router = useRouter();
const userRef = ref(getCurrentUser());
const avatarBroken = ref(false);
const avatarUrl = ref("");
const sidebarRef = ref(null);
const isSidebarExpanded = ref(readPersistedSidebarExpanded());
const backendUnavailable = ref(false);
const { success, info } = useToast();

const currentUser = computed(() => userRef.value);
const currentUserLabel = computed(() => formatUserLabel(currentUser.value));
const canViewLogs = computed(() => hasPermission(currentUser.value, "VIEW_LOGS"));
const navItems = computed(() => {
  const items = [
    { to: "/inbox", label: "My Inbox", icon: "inbox" },
    { to: "/documents", label: "Documents", icon: "documents" },
    { to: "/profile", label: "My Profile", icon: "profile" },
  ];

  if (canViewLogs.value) {
    items.push({ to: "/logs", label: "Logs", icon: "logs" });
  }

  if (currentUser.value?.role === "ADMIN") {
    items.push(
      { to: "/users", label: "Users", icon: "users" },
      { to: "/permissions", label: "Permissions", icon: "permissions" },
    );
  }

  return items;
});

const initials = computed(() => {
  const text = String(currentUser.value?.fullName || currentUser.value?.username || "U").trim();
  if (!text) return "U";
  const parts = text.split(/\s+/).slice(0, 2);
  return parts.map((p) => p[0]?.toUpperCase() || "").join("") || "U";
});

let avatarRequestId = 0;
let sidebarCollapseTimer = null;
let lastSidebarNavClickAt = 0;
const SIDEBAR_NAV_CLICK_GRACE_MS = 1000;

function clearSidebarCollapseTimer() {
  if (sidebarCollapseTimer !== null) {
    window.clearTimeout(sidebarCollapseTimer);
    sidebarCollapseTimer = null;
  }
}

function expandSidebar() {
  clearSidebarCollapseTimer();
  if (isSidebarExpanded.value) return;
  setPersistedSidebarExpanded(true);
  isSidebarExpanded.value = true;
}

function scheduleSidebarCollapse() {
  clearSidebarCollapseTimer();
  const navClickGraceRemaining = Math.max(0, SIDEBAR_NAV_CLICK_GRACE_MS - (Date.now() - lastSidebarNavClickAt));
  sidebarCollapseTimer = window.setTimeout(() => {
    setPersistedSidebarExpanded(false);
    isSidebarExpanded.value = false;
    sidebarCollapseTimer = null;
  }, 550 + navClickGraceRemaining);
}

function handleSidebarFocusOut(event) {
  const nextFocusedElement = event?.relatedTarget;
  if (nextFocusedElement && sidebarRef.value?.contains(nextFocusedElement)) {
    return;
  }

  scheduleSidebarCollapse();
}

function handleSidebarNavPress() {
  lastSidebarNavClickAt = Date.now();
  clearSidebarCollapseTimer();
  setPersistedSidebarExpanded(true);
  isSidebarExpanded.value = true;
}

function handleSidebarNavClick(event) {
  if (event?.detail > 0) return;
  handleSidebarNavPress();
}

async function refreshAvatarUrl() {
  const requestId = ++avatarRequestId;
  avatarBroken.value = false;

  if (!currentUser.value?.hasProfilePicture) {
    avatarUrl.value = "";
    return;
  }

  try {
    const url = await createMyProfilePictureUrl(currentUser.value?.profilePictureUpdatedAt || Date.now());
    if (requestId === avatarRequestId) avatarUrl.value = url;
  } catch {
    if (requestId === avatarRequestId) avatarUrl.value = "";
  }
}

watch(
  () => `${currentUser.value?.hasProfilePicture || false}:${currentUser.value?.profilePictureUpdatedAt || ""}`,
  () => {
    void refreshAvatarUrl();
  },
  { immediate: true }
);

function onAuthChanged() {
  userRef.value = getCurrentUser();
  avatarBroken.value = false;
}

function onBackendStatusChanged(event) {
  backendUnavailable.value = event?.detail?.status === "unavailable";
}

function dismissBackendBanner() {
  backendUnavailable.value = false;
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
  isSidebarExpanded.value = readPersistedSidebarExpanded();
  backendUnavailable.value = getBackendStatus() === "unavailable";
  window.addEventListener("rms_auth_changed", onAuthChanged);
  window.addEventListener("rms_backend_status_changed", onBackendStatusChanged);
  showPendingWelcome();
  if (isSidebarExpanded.value) {
    scheduleSidebarCollapse();
  }
});

onUnmounted(() => {
  window.removeEventListener("rms_auth_changed", onAuthChanged);
  window.removeEventListener("rms_backend_status_changed", onBackendStatusChanged);
  clearSidebarCollapseTimer();
});
</script>

<style scoped>
.app {
  --sidebar-collapsed-width: 74px;
  --sidebar-expanded-width: 220px;
  font-family: Arial, sans-serif;
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.header {
  height: 60px;
  background: #1f2937;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  line-height: 1.1;
}

.brand-logo {
  width: 42px;
  height: 42px;
  border-radius: 999px;
  background: #fff;
  display: grid;
  place-items: center;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.28);
  overflow: hidden;
  flex: 0 0 auto;
}

.brand-logo img {
  width: 34px;
  height: 34px;
  object-fit: contain;
  display: block;
}

.brand-copy { display: flex; flex-direction: column; }
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

.serverBanner {
  min-height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 8px 18px;
  border-bottom: 1px solid #f59e0b;
  background: #fffbeb;
  color: #92400e;
  font-size: 13px;
  font-weight: 700;
}

.serverBannerAction {
  border: 1px solid #f59e0b;
  background: #ffffff;
  color: #92400e;
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.sidebar {
  width: var(--sidebar-collapsed-width);
  flex: 0 0 var(--sidebar-collapsed-width);
  background: linear-gradient(180deg, #111827 0%, #0f172a 100%);
  color: #fff;
  padding: 14px 12px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
  overflow: hidden;
  transition: width .38s ease, padding .38s ease, box-shadow .38s ease, flex-basis .38s ease;
  border-right: 1px solid rgba(255,255,255,0.06);
  box-sizing: border-box;
}

.sidebar-expanded {
  width: var(--sidebar-expanded-width);
  flex-basis: var(--sidebar-expanded-width);
  padding-right: 16px;
  box-shadow: 12px 0 32px rgba(15, 23, 42, 0.14);
}

.sidebar-top {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 40px;
  padding: 2px 0 10px;
  width: 100%;
  box-sizing: border-box;
}

.sidebar-mark {
  width: 40px;
  min-width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: linear-gradient(135deg, #1d4ed8 0%, #2563eb 100%);
  box-shadow: 0 10px 20px rgba(37, 99, 235, 0.22);
  overflow: hidden;
}

.sidebar-mark-svg {
  width: 28px;
  height: 28px;
  display: block;
  stroke: #eff6ff;
  fill: none;
  stroke-width: 3.2;
  stroke-linecap: round;
}

.sidebar-heading {
  font-size: 13px;
  font-weight: 800;
  color: #e5e7eb;
}

.sidebar-text {
  opacity: 0;
  transform: translateX(-8px);
  transition: opacity .24s ease, transform .32s ease;
  white-space: nowrap;
}

.sidebar-expanded .sidebar-text {
  opacity: 1;
  transform: translateX(0);
}

.sidebar:not(.sidebar-expanded) .sidebar-top .sidebar-text {
  display: none;
}

.sidebar:not(.sidebar-expanded) .nav .sidebar-text {
  display: none;
}

.sidebar-expanded .sidebar-top {
  justify-content: flex-start;
}

.navList {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  width: 100%;
}

.sidebar:not(.sidebar-expanded) .navList {
  align-items: center;
}

.nav {
  color: #d1d5db;
  text-decoration: none;
  padding: 6px 8px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 50px;
  transition: background .24s ease, transform .24s ease, box-shadow .24s ease;
  width: 100%;
  box-sizing: border-box;
}

.sidebar:not(.sidebar-expanded) .nav {
  width: 44px;
  min-width: 44px;
  justify-content: center;
  padding: 4px;
}

.nav:hover {
  background: rgba(55, 65, 81, 0.78);
}
.nav.router-link-active {
  background: rgba(37, 99, 235, 0.18);
  color: #fff;
  font-weight: 700;
  box-shadow: inset 0 0 0 1px rgba(96, 165, 250, 0.26);
}

.content { flex: 1; background: #f3f4f6; padding: 20px; overflow-y: auto; }

.nav-icon {
  width: 36px;
  min-width: 36px;
  height: 36px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(255,255,255,0.07);
  color: #dbeafe;
  transition: background .24s ease, color .24s ease, transform .24s ease;
}

.nav-svg {
  width: 18px;
  height: 18px;
  stroke: currentColor;
  fill: none;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.nav.router-link-active .nav-icon {
  background: rgba(37, 99, 235, 0.5);
  color: #ffffff;
}

.sidebar:not(.sidebar-expanded) .nav.router-link-active .nav-icon {
  background: rgba(37, 99, 235, 0.58);
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.24);
}

.sidebar:not(.sidebar-expanded) .nav:hover {
  transform: translateY(-1px);
}

@media (max-width: 900px) {
  .sidebar,
  .sidebar-expanded {
    width: var(--sidebar-expanded-width);
    flex-basis: var(--sidebar-expanded-width);
  }

  .sidebar-text {
    opacity: 1;
    transform: translateX(0);
    display: inline;
  }

  .sidebar {
    padding: 16px;
  }

  .nav {
    justify-content: flex-start;
    padding: 8px;
  }
}
</style>
