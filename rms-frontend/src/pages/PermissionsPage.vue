<template>
  <AppLayout>
    <div class="pageHead">
      <div>
        <h2>Permissions</h2>
        <p class="pageSub">Admin can update role permissions without changing code.</p>
      </div>
      <div class="headActions">
        <button class="btn" :disabled="loading || saving" @click="load">Refresh</button>
        <button class="btn btn-primary" :disabled="loading || saving || !dirty" @click="save">
          {{ saving ? "Saving..." : "Save Changes" }}
        </button>
      </div>
    </div>

    <div v-if="!isAdmin" class="errorBox">Only ADMIN can access permission management.</div>

    <div v-else class="card">
      <div v-if="error" class="errorBox">{{ error }}</div>
      <div v-if="success" class="successBox">{{ success }}</div>

      <div class="tableWrap">
        <table class="table">
          <thead>
            <tr>
              <th>Permission</th>
              <th v-for="roleName in roles" :key="`head-${roleName}`">{{ roleName }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td :colspan="roles.length + 1" class="muted">Loading permissions...</td>
            </tr>
            <tr v-else-if="permissions.length === 0">
              <td :colspan="roles.length + 1" class="muted">No permissions found.</td>
            </tr>
            <tr v-else v-for="permission in permissions" :key="permission">
              <td>
                <div class="permTitle">{{ friendlyLabel(permission) }}</div>
                <div class="permCode">{{ permission }}</div>
              </td>
              <td v-for="roleName in roles" :key="`${permission}-${roleName}`" class="checkCell">
                <label class="toggleWrap">
                  <input
                    type="checkbox"
                    :checked="isEnabled(roleName, permission)"
                    @change="setEnabled(roleName, permission, $event.target.checked)"
                  />
                  <span>{{ isEnabled(roleName, permission) ? "Yes" : "No" }}</span>
                </label>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, ref } from "vue";
import AppLayout from "../layouts/AppLayout.vue";
import { adminGetPermissionsMatrix, adminUpdatePermissionsMatrix } from "../api/auth.api";
import { getCurrentUser } from "../auth/currentUser";

const user = ref(getCurrentUser());
const isAdmin = computed(() => user.value?.role === "ADMIN");

const loading = ref(false);
const saving = ref(false);
const error = ref("");
const success = ref("");
const roles = ref([]);
const permissions = ref([]);
const matrix = ref({});
const dirty = ref(false);

function cellKey(roleName, permission) {
  return `${roleName}::${permission}`;
}

function buildMatrix(entries) {
  const next = {};
  for (const entry of entries || []) {
    next[cellKey(entry.roleName, entry.permission)] = !!entry.enabled;
  }
  return next;
}

function isEnabled(roleName, permission) {
  return !!matrix.value[cellKey(roleName, permission)];
}

function setEnabled(roleName, permission, enabled) {
  matrix.value = {
    ...matrix.value,
    [cellKey(roleName, permission)]: !!enabled,
  };
  dirty.value = true;
}

function friendlyLabel(permission) {
  return String(permission || "")
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

async function load() {
  loading.value = true;
  error.value = "";
  success.value = "";

  try {
    const data = await adminGetPermissionsMatrix();
    roles.value = Array.isArray(data?.roles) ? data.roles : [];
    permissions.value = Array.isArray(data?.permissions) ? data.permissions : [];
    matrix.value = buildMatrix(data?.entries);
    dirty.value = false;
  } catch (e) {
    error.value = e?.message || "Failed to load permissions.";
  } finally {
    loading.value = false;
  }
}

async function save() {
  saving.value = true;
  error.value = "";
  success.value = "";

  try {
    const entries = [];
    for (const permission of permissions.value) {
      for (const roleName of roles.value) {
        entries.push({
          roleName,
          permission,
          enabled: isEnabled(roleName, permission),
        });
      }
    }

    const data = await adminUpdatePermissionsMatrix(entries);
    roles.value = Array.isArray(data?.roles) ? data.roles : roles.value;
    permissions.value = Array.isArray(data?.permissions) ? data.permissions : permissions.value;
    matrix.value = buildMatrix(data?.entries);
    dirty.value = false;
    success.value = "Permissions updated successfully.";
  } catch (e) {
    error.value = e?.message || "Failed to save permissions.";
  } finally {
    saving.value = false;
  }
}

load();
</script>

<style scoped>
.pageHead {
  display:flex;
  align-items:flex-start;
  justify-content:space-between;
  gap:12px;
  margin-bottom:14px;
}

h2 { margin:0; }
.pageSub { margin:4px 0 0; color:#6b7280; font-size:13px; }
.headActions { display:flex; gap:10px; }

.card {
  background:#fff;
  border:1px solid #e5e7eb;
  border-radius:14px;
  padding:16px;
}

.tableWrap { overflow:auto; }
.table { width:100%; border-collapse:collapse; min-width:900px; }
.table th, .table td { border-bottom:1px solid #e5e7eb; padding:12px 10px; text-align:left; vertical-align:top; }
.table th { font-size:12px; text-transform:uppercase; letter-spacing:0.04em; color:#6b7280; background:#f9fafb; }

.permTitle { font-weight:700; color:#111827; }
.permCode { font-size:11px; color:#6b7280; margin-top:4px; }
.checkCell { text-align:center; }
.toggleWrap { display:inline-flex; align-items:center; gap:8px; font-size:13px; color:#374151; }

.btn {
  padding:10px 12px;
  border-radius:8px;
  border:1px solid #e5e7eb;
  background:#fff;
  cursor:pointer;
}
.btn-primary { background:#2563eb; border-color:#2563eb; color:#fff; }
.btn:disabled { opacity:0.6; cursor:not-allowed; }

.errorBox { background:#fef2f2; border:1px solid #fecaca; color:#991b1b; padding:10px 12px; border-radius:8px; margin-bottom:12px; }
.successBox { background:#ecfdf5; border:1px solid #a7f3d0; color:#065f46; padding:10px 12px; border-radius:8px; margin-bottom:12px; }
.muted { color:#6b7280; text-align:center; }
</style>