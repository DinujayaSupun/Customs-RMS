<template>
  <div class="app">
    <header class="header">
      <div class="brand">
        <span class="brand-name">Sri Lanka Customs</span>
        <span class="brand-sub">Document Workflow Management System</span>
      </div>

      <div class="user">
        <span class="user-role">Role: {{ currentUser.role }} (id={{ currentUser.id }})</span>

        <select class="user-select" v-model.number="selectedId" @change="onChange">
          <option v-for="u in users" :key="u.id" :value="u.id">
            {{ u.name }} ({{ u.role }}) — id={{ u.id }}
          </option>
        </select>

        <button class="logout" type="button" @click="resetUser">Reset</button>
      </div>
    </header>

    <div class="body">
      <aside class="sidebar">
        <div class="sidebar-title">Navigation</div>

        <router-link to="/documents" class="nav">Documents</router-link>
        <router-link to="/inbox" class="nav">My Inbox</router-link>
        <router-link to="/logs" class="nav">Logs</router-link>
        <router-link to="/users" class="nav">Users</router-link>
      </aside>

      <main class="content">
        <slot />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from "vue";
import { getUsers, getCurrentUser, setCurrentUser, clearCurrentUser } from "../auth/currentUser";

const users = getUsers();
const selectedId = ref(users[0].id);

const currentUser = computed(() => getCurrentUser());

function onChange() {
  const u = users.find((x) => x.id === Number(selectedId.value));
  if (u) setCurrentUser(u);
}

function resetUser() {
  clearCurrentUser();
  selectedId.value = getCurrentUser().id;
}

onMounted(() => {
  selectedId.value = getCurrentUser().id;
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

.user-select {
  height: 34px;
  border-radius: 8px;
  border: 1px solid rgba(255,255,255,0.35);
  background: rgba(255,255,255,0.08);
  color: #fff;
  padding: 0 10px;
  outline: none;
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