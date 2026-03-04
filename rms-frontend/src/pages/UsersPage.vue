<template>
  <AppLayout>
    <div class="pageHead">
      <h2>Users</h2>
      <div class="headActions">
        <button class="btn" @click="load" :disabled="loading">Refresh</button>
        <button class="btn" @click="downloadCsv">Export CSV</button>
      </div>
    </div>

    <div v-if="!isAdmin" class="errorBox">Only ADMIN can access user management.</div>

    <div v-else class="card">
      <div class="filters">
        <input v-model="search" class="input" placeholder="Search name/username/email/phone/department" />
        <select v-model="role" class="input">
          <option value="">All Roles</option>
          <option v-for="r in roles" :key="r" :value="r">{{ r }}</option>
        </select>
        <select v-model="active" class="input">
          <option value="">All Status</option>
          <option value="true">Active</option>
          <option value="false">Inactive</option>
        </select>
        <button class="btn" @click="load">Apply</button>
      </div>

      <div class="createBox">
        <div class="createTitle">Create User</div>
        <div class="createGrid">
          <input v-model="createForm.fullName" class="input" placeholder="Full name" />
          <input v-model="createForm.username" class="input" placeholder="Username" />
          <input v-model="createForm.email" class="input" placeholder="Email" />
          <input v-model="createForm.phone" class="input" placeholder="Phone" />
          <input v-model="createForm.department" class="input" placeholder="Department" />
          <select v-model="createForm.role" class="input">
            <option value="">Select role</option>
            <option v-for="r in roles" :key="`create-${r}`" :value="r">{{ r }}</option>
          </select>
          <input v-model="createForm.password" class="input" type="password" placeholder="Password" />
          <button class="btn btn-primary" :disabled="loading" @click="createUser">Create</button>
        </div>
      </div>

      <div v-if="error" class="errorBox">{{ error }}</div>

      <table class="table">
        <thead>
          <tr>
            <th>Name</th>
            <th>Username</th>
            <th>Role</th>
            <th>Contact</th>
            <th>Department</th>
            <th>Status</th>
            <th style="width: 260px">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7" class="muted">Loading...</td>
          </tr>
          <tr v-else-if="rows.length === 0">
            <td colspan="7" class="muted">No users found.</td>
          </tr>
          <tr v-else v-for="u in rows" :key="u.id">
            <td>{{ u.fullName }} • {{ u.role }} • ID {{ u.id }}</td>
            <td>{{ u.username }}</td>
            <td>{{ u.role }}</td>
            <td>
              <div>{{ u.email || "-" }}</div>
              <div class="small">{{ u.phone || "-" }}</div>
            </td>
            <td>{{ u.department || "-" }}</td>
            <td>
              <span class="pill" :class="u.active ? 'pill-active' : 'pill-inactive'">{{ u.active ? 'ACTIVE' : 'INACTIVE' }}</span>
            </td>
            <td>
              <div class="actions">
                <button class="btn btn-sm" @click="editUser(u)">Edit</button>
                <button class="btn btn-sm" @click="resetUserPassword(u)">Reset Password</button>
                <button
                  v-if="u.active"
                  class="btn btn-sm danger"
                  :disabled="u.role === 'ADMIN'"
                  @click="deactivateUser(u)"
                >Deactivate</button>
                <button v-else class="btn btn-sm" @click="activateUser(u)">Activate</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pager">
        <button class="btn btn-sm" :disabled="page===0 || loading" @click="page = page - 1; load()">Prev</button>
        <span>Page {{ page + 1 }}</span>
        <button class="btn btn-sm" :disabled="last || loading" @click="page = page + 1; load()">Next</button>
      </div>

      <div class="dupBox">
        <div class="createTitle">Duplicate Users (same Name + Role)</div>
        <div v-if="duplicateGroups.length === 0" class="muted">No duplicate candidates found.</div>

        <div v-for="group in duplicateGroups" :key="`${group.fullName}-${group.role}`" class="dupGroup">
          <div class="dupHead">{{ group.fullName }} • {{ group.role }}</div>

          <div class="dupRows">
            <div v-for="u in group.users" :key="`dup-${u.id}`" class="dupRow">
              <span>{{ u.fullName }} • {{ u.role }} • ID {{ u.id }} <small>({{ u.active ? 'ACTIVE' : 'INACTIVE' }})</small></span>
              <button
                class="btn btn-sm"
                :disabled="!u.active || !mergeTargetId(group.users, u.id)"
                @click="mergeDuplicate(u.id, mergeTargetId(group.users, u.id))"
              >
                Merge Into {{ mergeTargetId(group.users, u.id) ? ('ID ' + mergeTargetId(group.users, u.id)) : '-' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="deactivateModalOpen" class="overlay" @click.self="closeDeactivateModal">
      <div class="modal">
        <div class="modalHead">
          <div>
            <div class="modalTitle">Deactivate User</div>
            <div class="modalSub">Select fallback DC owner for active document transfer.</div>
          </div>
          <button class="btn btn-sm" @click="closeDeactivateModal">Close</button>
        </div>

        <div class="modalBody">
          <div class="small" style="margin-bottom:8px;">
            Deactivating: <b>{{ deactivateTargetUser?.fullName }} • {{ deactivateTargetUser?.role }} • ID {{ deactivateTargetUser?.id }}</b>
          </div>

          <select v-model.number="deactivateFallbackDcUserId" class="input">
            <option
              v-for="dc in availableFallbackDcs(deactivateTargetUser?.id)"
              :key="`fallback-${dc.id}`"
              :value="dc.id"
            >
              {{ dc.fullName }} • {{ dc.role }} • ID {{ dc.id }}
            </option>
          </select>
        </div>

        <div class="modalFoot">
          <button class="btn" @click="closeDeactivateModal">Cancel</button>
          <button class="btn btn-primary" @click="confirmDeactivate">Confirm Deactivate</button>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import AppLayout from "../layouts/AppLayout.vue";
import { getCurrentUser } from "../auth/currentUser";
import {
  adminActivateUser,
  adminCreateUser,
  adminDeactivateUser,
  adminListDuplicateUsers,
  adminListRoles,
  adminListUsers,
  adminMergeUsers,
  adminResetPassword,
  adminUpdateUser,
} from "../api/auth.api";

const user = ref(getCurrentUser());
const isAdmin = computed(() => user.value?.role === "ADMIN");

const loading = ref(false);
const error = ref("");
const rows = ref([]);
const roles = ref([]);
const page = ref(0);
const size = ref(20);
const last = ref(false);
const duplicateGroups = ref([]);

const deactivateModalOpen = ref(false);
const deactivateTargetUser = ref(null);
const deactivateFallbackDcUserId = ref(null);

const search = ref("");
const role = ref("");
const active = ref("");

const createForm = ref({
  fullName: "",
  username: "",
  email: "",
  phone: "",
  department: "",
  role: "",
  password: "",
});

function normalizeBlank(value) {
  const v = String(value ?? "").trim();
  return v || null;
}

async function loadRoles() {
  try {
    roles.value = await adminListRoles();
  } catch {
    roles.value = ["ADMIN", "DC", "DDC", "SC", "ASC", "PMA"];
  }
}

async function load() {
  if (!isAdmin.value) return;
  loading.value = true;
  error.value = "";
  try {
    const data = await adminListUsers({
      page: page.value,
      size: size.value,
      search: search.value || undefined,
      role: role.value || undefined,
      active: active.value === "" ? undefined : active.value,
    });
    rows.value = data?.content ?? [];
    last.value = !!data?.last;
    await loadDuplicates();
  } catch (e) {
    error.value = e?.message || "Failed to load users";
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

async function loadDuplicates() {
  try {
    duplicateGroups.value = await adminListDuplicateUsers();
  } catch {
    duplicateGroups.value = [];
  }
}

async function createUser() {
  error.value = "";
  try {
    await adminCreateUser({
      fullName: createForm.value.fullName,
      username: createForm.value.username,
      email: normalizeBlank(createForm.value.email),
      phone: normalizeBlank(createForm.value.phone),
      department: normalizeBlank(createForm.value.department),
      role: createForm.value.role,
      password: createForm.value.password,
    });
    createForm.value = { fullName: "", username: "", email: "", phone: "", department: "", role: "", password: "" };
    await load();
  } catch (e) {
    error.value = e?.message || "Create user failed";
  }
}

async function editUser(u) {
  const fullName = window.prompt("Full name", u.fullName);
  if (fullName === null) return;
  const email = window.prompt("Email", u.email || "");
  if (email === null) return;
  const phone = window.prompt("Phone", u.phone || "");
  if (phone === null) return;
  const department = window.prompt("Department", u.department || "");
  if (department === null) return;

  try {
    await adminUpdateUser(u.id, {
      fullName,
      email: normalizeBlank(email),
      phone: normalizeBlank(phone),
      department: normalizeBlank(department),
    });
    await load();
  } catch (e) {
    error.value = e?.message || "Update failed";
  }
}

async function resetUserPassword(u) {
  const newPassword = window.prompt(`New password for ${u.username}`);
  if (!newPassword) return;
  try {
    await adminResetPassword(u.id, newPassword);
  } catch (e) {
    error.value = e?.message || "Reset password failed";
  }
}

async function deactivateUser(u) {
  const activeDcs = availableFallbackDcs(u.id);
  if (activeDcs.length === 0) {
    error.value = "No active fallback DC user available.";
    return;
  }

  deactivateTargetUser.value = u;
  deactivateFallbackDcUserId.value = activeDcs[0]?.id ?? null;
  deactivateModalOpen.value = true;
}

function availableFallbackDcs(excludeUserId) {
  return rows.value.filter((x) => x.active && x.role === "DC" && Number(x.id) !== Number(excludeUserId));
}

function closeDeactivateModal() {
  deactivateModalOpen.value = false;
  deactivateTargetUser.value = null;
  deactivateFallbackDcUserId.value = null;
}

async function confirmDeactivate() {
  if (!deactivateTargetUser.value || !deactivateFallbackDcUserId.value) return;
  try {
    await adminDeactivateUser(deactivateTargetUser.value.id, Number(deactivateFallbackDcUserId.value));
    closeDeactivateModal();
    await load();
  } catch (e) {
    error.value = e?.message || "Deactivate failed";
  }
}

function mergeTargetId(groupUsers, sourceId) {
  return groupUsers.find((u) => Number(u.id) !== Number(sourceId) && u.active)?.id ?? null;
}

async function mergeDuplicate(sourceUserId, targetUserId) {
  if (!sourceUserId || !targetUserId) return;
  try {
    await adminMergeUsers(Number(sourceUserId), Number(targetUserId));
    await load();
  } catch (e) {
    error.value = e?.message || "Merge failed";
  }
}

async function activateUser(u) {
  try {
    await adminActivateUser(u.id);
    await load();
  } catch (e) {
    error.value = e?.message || "Activate failed";
  }
}

async function downloadCsv() {
  try {
    const data = await adminListUsers({
      page: 0,
      size: 10000,
      search: search.value || undefined,
      role: role.value || undefined,
      active: active.value === "" ? undefined : active.value,
    });
    const rowsData = data?.content ?? [];
    const header = ["id", "fullName", "username", "email", "phone", "department", "role", "active", "createdAt"];
    const body = rowsData.map((u) => [u.id, u.fullName, u.username, u.email || "", u.phone || "", u.department || "", u.role, u.active, u.createdAt]);
    const csv = [header, ...body].map((line) => line.map((v) => `"${String(v ?? "").replaceAll('"', '""')}"`).join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
    const href = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = href;
    a.download = "users-export.csv";
    a.click();
    URL.revokeObjectURL(href);
  } catch (e) {
    error.value = e?.message || "Export failed";
  }
}

onMounted(async () => {
  if (!isAdmin.value) return;
  await loadRoles();
  await load();
});
</script>

<style scoped>
.pageHead { display:flex; align-items:center; justify-content:space-between; margin-bottom:14px; }
.headActions { display:flex; gap:8px; }
.card {
  background: white;
  padding: 20px;
  border-radius: 10px;
}
.filters { display:grid; grid-template-columns:2fr 1fr 1fr auto; gap:10px; margin-bottom:12px; }
.createBox { border:1px solid #e5e7eb; border-radius:10px; padding:12px; margin-bottom:12px; }
.createTitle { font-size:13px; font-weight:800; margin-bottom:8px; }
.createGrid { display:grid; grid-template-columns:repeat(4, minmax(0, 1fr)); gap:8px; }
.table { width:100%; border-collapse:collapse; }
.table th, .table td { padding:10px; border-bottom:1px solid #eee; text-align:left; }
.muted { color:#6b7280; text-align:center; }
.input { height:38px; border-radius:8px; border:1px solid #e5e7eb; padding:0 10px; outline:none; width:100%; }
.btn { padding:10px 12px; border-radius:8px; border:1px solid #e5e7eb; background:#fff; cursor:pointer; }
.btn:hover { background:#f9fafb; }
.btn-primary { background:#2563eb; border-color:#2563eb; color:#fff; }
.btn-primary:hover { background:#1d4ed8; }
.btn-sm { padding:6px 8px; font-size:12px; }
.actions { display:flex; gap:6px; flex-wrap:wrap; }
.pill { display:inline-block; padding:3px 8px; border-radius:999px; font-size:11px; font-weight:700; }
.pill-active { background:#ecfdf5; color:#065f46; border:1px solid #a7f3d0; }
.pill-inactive { background:#fef2f2; color:#991b1b; border:1px solid #fecaca; }
.small { color:#6b7280; font-size:12px; }
.danger { border-color:#fecaca; color:#991b1b; }
.errorBox {
  margin-bottom:12px;
  background:#fef2f2;
  border:1px solid #fecaca;
  color:#991b1b;
  padding:10px 12px;
  border-radius:8px;
}
.pager { display:flex; align-items:center; justify-content:flex-end; gap:10px; margin-top:10px; }
.dupBox { margin-top:14px; border-top:1px solid #eee; padding-top:12px; }
.dupGroup { border:1px solid #eee; border-radius:8px; padding:10px; margin-top:8px; }
.dupHead { font-weight:700; margin-bottom:8px; }
.dupRows { display:flex; flex-direction:column; gap:8px; }
.dupRow { display:flex; align-items:center; justify-content:space-between; gap:10px; }

.overlay {
  position:fixed;
  inset:0;
  background:rgba(0,0,0,0.35);
  display:flex;
  align-items:center;
  justify-content:center;
  padding:14px;
}

.modal {
  width:100%;
  max-width:620px;
  background:#fff;
  border-radius:10px;
  overflow:hidden;
}

.modalHead {
  display:flex;
  align-items:flex-start;
  justify-content:space-between;
  padding:14px 16px;
  border-bottom:1px solid #eee;
}

.modalTitle { font-size:14px; font-weight:800; }
.modalSub { font-size:12px; color:#6b7280; margin-top:2px; }
.modalBody { padding:14px 16px; }

.modalFoot {
  padding:14px 16px;
  border-top:1px solid #eee;
  display:flex;
  justify-content:flex-end;
  gap:8px;
}
</style>
