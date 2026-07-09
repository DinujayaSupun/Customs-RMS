<template>
  <AppLayout>
    <div class="groupsPage">
      <div class="pageGlow pageGlowA"></div>
      <div class="pageGlow pageGlowB"></div>

      <div class="pageHead">
        <div>
          <h2>Groups</h2>
          <p class="pageSub">
            Forward a document to a group and any admin can act on it — like a WhatsApp group, members stay copied in.
          </p>
        </div>
        <div class="headActions">
          <button class="btn" @click="load" :disabled="loading || saving">Refresh</button>
          <button v-if="canCreateGroups" class="btn btn-primary" @click="openCreateModal">Create Group</button>
        </div>
      </div>

      <div v-if="error" class="errorBox">{{ error }}</div>

      <div class="card">
        <section class="section">
          <div class="sectionHead">
            <div>
              <div class="sectionEyebrow">Find Groups</div>
              <h3>Search</h3>
              <p>Filter by group name or member name.</p>
            </div>
            <span class="tableMeta">{{ filteredGroups.length }} of {{ groups.length }} groups</span>
          </div>

          <div class="field wide">
            <label>Search</label>
            <input v-model="search" class="input" placeholder="Search group name or member..." />
          </div>
        </section>

        <div v-if="loading" class="emptyPanel">Loading groups...</div>
        <div v-else-if="filteredGroups.length === 0" class="emptyPanel">
          {{ groups.length === 0 ? "No groups yet — create one." : "No groups match your search." }}
        </div>

        <div v-else class="groupList">
          <div v-for="g in filteredGroups" :key="g.id" class="groupCard">
            <div class="groupCardHead">
              <div class="groupAvatar" :style="avatarStyle(g)">{{ initials(g.name) }}</div>
              <div class="groupInfo">
                <div class="groupName">{{ g.name }}</div>
                <div class="groupMeta">
                  Created by {{ g.createdByName || "Unknown" }} · {{ g.adminCount }} admin{{ g.adminCount === 1 ? "" : "s" }} · {{ g.memberCount }} member{{ g.memberCount === 1 ? "" : "s" }}
                </div>
              </div>
              <span v-if="g.documentsHeldCount > 0" class="pill pill-active">
                Holds {{ g.documentsHeldCount }} document{{ g.documentsHeldCount === 1 ? "" : "s" }}
              </span>
            </div>

            <div class="memberChips">
              <span v-for="m in g.members" :key="m.userId" class="memberChip" :class="{ adminChip: m.isAdmin }">
                {{ m.fullName }}{{ m.isAdmin ? " (Admin)" : "" }}
              </span>
            </div>

            <div class="groupCardFoot">
              <button class="btn btn-sm" @click="toggleExpand(g)">
                {{ expandedGroupId === g.id ? "Hide held documents" : "View held documents" }}
              </button>
              <div class="actions">
                <button class="btn btn-sm" :disabled="!canManageGroup(g)" @click="openEditModal(g)">Edit</button>
                <button class="btn btn-sm danger" :disabled="!canManageGroup(g)" @click="openDeleteModal(g)">Delete</button>
              </div>
            </div>

            <div v-if="expandedGroupId === g.id" class="heldDocs">
              <div v-if="heldDocsLoading" class="muted">Loading...</div>
              <div v-else-if="heldDocs.length === 0" class="muted">This group isn't holding any documents.</div>
              <ul v-else class="heldDocsList">
                <li v-for="d in heldDocs" :key="d.id">
                  <router-link :to="`/documents/${d.id}`">{{ d.refNo }} — {{ d.title }}</router-link>
                  <span class="tableMeta">{{ d.status }}</span>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>

      <!-- Create / Edit modal -->
      <div v-if="modalOpen" class="overlay">
        <div class="modal">
          <div class="modalHead">
            <div>
              <div class="modalTitle">{{ editingGroup ? "Edit Group" : "Create Group" }}</div>
              <div class="modalSub">Groups let you forward a document to a whole team — any admin can act on it.</div>
            </div>
            <button class="btn btn-sm" @click="closeModal">Close</button>
          </div>

          <div class="modalBody formStack">
            <div v-if="modalError" class="modalAlert modalAlertDanger">{{ modalError }}</div>

            <div class="field">
              <label>Group Name</label>
              <input v-model="form.name" class="input" placeholder="e.g. Clearance Unit A" :disabled="saving" />
            </div>

            <div class="field">
              <label>Color</label>
              <div class="colorSwatches">
                <button
                  v-for="c in colorPalette"
                  :key="c"
                  type="button"
                  class="colorSwatch"
                  :class="{ active: form.color === c }"
                  :style="{ background: c }"
                  :disabled="saving"
                  @click="form.color = c"
                ></button>
              </div>
            </div>

            <div class="field">
              <label>Members</label>
              <RecipientChipPicker
                v-model="selectedMemberIds"
                :users="allUsers"
                :disabled="saving"
                placeholder="No members selected"
                search-placeholder="Search users to add..."
              />
            </div>

            <div v-if="selectedMemberIds.length" class="field">
              <label>Admin / Member</label>
              <div class="memberBuilder">
                <div v-for="m in selectedMemberDetails" :key="m.id" class="memberBuilderRow">
                  <div class="memberBuilderName">{{ formatUserLabel(m.user) }}</div>
                  <label class="adminToggle">
                    <input type="checkbox" :checked="isAdminSelected(m.id)" :disabled="saving" @change="toggleMemberAdmin(m.id)" />
                    Admin
                  </label>
                </div>
              </div>
              <span class="tableMeta">
                {{ adminCountInForm }} admin{{ adminCountInForm === 1 ? "" : "s" }} · {{ selectedMemberIds.length }} member{{ selectedMemberIds.length === 1 ? "" : "s" }}
              </span>
            </div>
          </div>

          <div class="modalFoot">
            <button class="btn" @click="closeModal" :disabled="saving">Cancel</button>
            <button class="btn btn-primary" :disabled="saving" @click="save">{{ saving ? "Saving..." : "Save" }}</button>
          </div>
        </div>
      </div>

      <!-- Delete modal -->
      <div v-if="deleteModalOpen" class="overlay">
        <div class="modal">
          <div class="modalHead">
            <div>
              <div class="modalTitle">Delete Group</div>
              <div class="modalSub">{{ deleteTarget?.name }}</div>
            </div>
            <button class="btn btn-sm" @click="closeDeleteModal">Close</button>
          </div>
          <div class="modalBody formStack">
            <div v-if="deleteModalError" class="modalAlert modalAlertDanger">{{ deleteModalError }}</div>
            <div class="noticeLine">This will permanently remove the group. This cannot be undone.</div>
          </div>
          <div class="modalFoot">
            <button class="btn" @click="closeDeleteModal" :disabled="saving">Cancel</button>
            <button class="btn btn-danger" :disabled="saving" @click="confirmDelete">{{ saving ? "Deleting..." : "Delete Group" }}</button>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import AppLayout from "../layouts/AppLayout.vue";
import RecipientChipPicker from "../components/RecipientChipPicker.vue";
import { getCurrentUser, hasPermission } from "../auth/currentUser";
import { formatUserLabel } from "../auth/userLabel";
import { useToast } from "../composables/useToast";
import { listGroups, createGroup, updateGroup, deleteGroup, getGroupDocuments, listUsers } from "../api/auth.api";
import { buildGroupMembersPayload, countAdmins, filterGroupsBySearch, initialsFor } from "../utils/groupsLogic";

const toast = useToast();
const currentUser = ref(getCurrentUser());
const canCreateGroups = computed(() => hasPermission(currentUser.value, "MANAGE_GROUPS"));

const colorPalette = ["#2563eb", "#16a34a", "#dc2626", "#d97706", "#7c3aed", "#0ea5e9", "#db2777", "#334155"];

const groups = ref([]);
const allUsers = ref([]);
const loading = ref(false);
const saving = ref(false);
const error = ref("");
const search = ref("");

onMounted(async () => {
  await Promise.all([load(), loadUsers()]);
});

async function load() {
  loading.value = true;
  error.value = "";
  try {
    groups.value = await listGroups();
  } catch (e) {
    error.value = e?.message || "Failed to load groups";
  } finally {
    loading.value = false;
  }
}

async function loadUsers() {
  try {
    allUsers.value = await listUsers();
  } catch {
    // Non-fatal for viewing groups; only blocks creating/editing membership.
  }
}

const filteredGroups = computed(() => filterGroupsBySearch(groups.value, search.value));

function initials(name) {
  return initialsFor(name);
}

function avatarStyle(g) {
  return { background: g.color || "#64748b", color: "#fff" };
}

function canManageGroup(g) {
  if (currentUser.value?.role === "ADMIN") return true;
  const me = (g.members || []).find((m) => Number(m.userId) === Number(currentUser.value?.id));
  return Boolean(me?.isAdmin);
}

// ---- expand / held documents ----
const expandedGroupId = ref(null);
const heldDocs = ref([]);
const heldDocsLoading = ref(false);

async function toggleExpand(g) {
  if (expandedGroupId.value === g.id) {
    expandedGroupId.value = null;
    return;
  }
  expandedGroupId.value = g.id;
  heldDocs.value = [];
  heldDocsLoading.value = true;
  try {
    heldDocs.value = await getGroupDocuments(g.id);
  } catch (e) {
    toast.error(e?.message || "Failed to load held documents");
  } finally {
    heldDocsLoading.value = false;
  }
}

// ---- create / edit modal ----
const modalOpen = ref(false);
const editingGroup = ref(null);
const modalError = ref("");
const form = ref({ name: "", color: colorPalette[0] });
const selectedMemberIds = ref([]);
const memberAdminFlags = ref({});

// listUsers() only returns non-admin accounts, so the signed-in ADMIN (auto-added as a member
// on create) would otherwise resolve to nothing here - fall back to their own profile, then a
// plain placeholder.
const selectedMemberDetails = computed(() =>
  selectedMemberIds.value.map((id) => {
    const fromUsers = allUsers.value.find((u) => String(u.id) === String(id));
    const isMe = currentUser.value && String(currentUser.value.id) === String(id);
    const fallback = isMe ? currentUser.value : { id, fullName: `User ${id}` };
    return { id, user: fromUsers || fallback };
  })
);

const adminCountInForm = computed(() => countAdmins(selectedMemberIds.value, memberAdminFlags.value));

function isAdminSelected(id) {
  return Boolean(memberAdminFlags.value[id]);
}

function toggleMemberAdmin(id) {
  memberAdminFlags.value = { ...memberAdminFlags.value, [id]: !memberAdminFlags.value[id] };
}

function openCreateModal() {
  editingGroup.value = null;
  form.value = { name: "", color: colorPalette[0] };
  const myId = currentUser.value?.id ? String(currentUser.value.id) : "";
  selectedMemberIds.value = myId ? [myId] : [];
  memberAdminFlags.value = myId ? { [myId]: true } : {};
  modalError.value = "";
  modalOpen.value = true;
}

function openEditModal(g) {
  editingGroup.value = g;
  form.value = { name: g.name, color: g.color || colorPalette[0] };
  selectedMemberIds.value = (g.members || []).map((m) => String(m.userId));
  const flags = {};
  (g.members || []).forEach((m) => {
    flags[String(m.userId)] = Boolean(m.isAdmin);
  });
  memberAdminFlags.value = flags;
  modalError.value = "";
  modalOpen.value = true;
}

function closeModal() {
  modalOpen.value = false;
  editingGroup.value = null;
}

async function save() {
  modalError.value = "";
  const name = form.value.name.trim();
  if (!name) {
    modalError.value = "Group name is required.";
    return;
  }
  if (selectedMemberIds.value.length === 0) {
    modalError.value = "Select at least one member.";
    return;
  }
  if (adminCountInForm.value === 0) {
    modalError.value = "At least one member must be an admin.";
    return;
  }

  const payload = {
    name,
    color: form.value.color,
    members: buildGroupMembersPayload(selectedMemberIds.value, memberAdminFlags.value),
  };

  saving.value = true;
  try {
    if (editingGroup.value) {
      await updateGroup(editingGroup.value.id, payload);
      toast.success("Group updated.");
    } else {
      await createGroup(payload);
      toast.success("Group created.");
    }
    closeModal();
    await load();
  } catch (e) {
    modalError.value = e?.message || "Save failed";
    toast.error(modalError.value);
  } finally {
    saving.value = false;
  }
}

// ---- delete modal ----
const deleteModalOpen = ref(false);
const deleteTarget = ref(null);
const deleteModalError = ref("");

function openDeleteModal(g) {
  deleteTarget.value = g;
  deleteModalError.value = "";
  deleteModalOpen.value = true;
}

function closeDeleteModal() {
  deleteModalOpen.value = false;
  deleteTarget.value = null;
}

async function confirmDelete() {
  if (!deleteTarget.value) return;
  saving.value = true;
  deleteModalError.value = "";
  try {
    await deleteGroup(deleteTarget.value.id);
    toast.success("Group deleted.");
    closeDeleteModal();
    await load();
  } catch (e) {
    deleteModalError.value = e?.message || "Delete failed";
    toast.error(deleteModalError.value);
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.groupsPage {
  position: relative;
  padding: 4px 2px 0;
}

.pageGlow {
  position: absolute;
  border-radius: 999px;
  filter: blur(44px);
  z-index: 0;
  pointer-events: none;
  opacity: 0.55;
}

.pageGlowA {
  width: 220px;
  height: 220px;
  right: 2%;
  top: -12px;
  background: radial-gradient(circle at center, rgba(37, 99, 235, 0.3), rgba(37, 99, 235, 0));
}

.pageGlowB {
  width: 180px;
  height: 180px;
  left: 6%;
  top: 42%;
  background: radial-gradient(circle at center, rgba(14, 116, 144, 0.22), rgba(14, 116, 144, 0));
}

.pageHead {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

h2 {
  margin: 0;
  font-size: 1.45rem;
  line-height: 1.15;
  letter-spacing: -0.02em;
  color: #0f172a;
}

.pageSub {
  margin: 6px 0 0;
  color: #475569;
  font-size: 13px;
  max-width: 760px;
}

.headActions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.card {
  position: relative;
  z-index: 1;
  background: linear-gradient(160deg, #ffffff 0%, #f8fbff 100%);
  border: 1px solid #dbe8ff;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
  padding: 20px;
  border-radius: 14px;
}

.section {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #ffffff;
  padding: 16px;
  margin-bottom: 14px;
}

.sectionHead {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.sectionHead h3 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
}

.sectionHead p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.sectionEyebrow {
  color: #2563eb;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  margin-bottom: 5px;
}

.tableMeta {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field label {
  font-size: 12px;
  font-weight: 700;
  color: #334155;
}

.wide {
  min-width: 0;
}

.input {
  height: 40px;
  border-radius: 10px;
  border: 1px solid #d1d5db;
  background: #fff;
  padding: 0 10px;
  outline: none;
  width: 100%;
  box-sizing: border-box;
  transition: border-color .2s ease, box-shadow .2s ease;
}

.input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.14);
}

.btn {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid #d1d5db;
  background: #fff;
  cursor: pointer;
  transition: all .2s ease;
}

.btn:hover:not(:disabled) {
  background: #f9fafb;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: #1d4ed8;
}

.btn-sm {
  padding: 6px 8px;
  font-size: 12px;
}

.btn-danger {
  background: #dc2626;
  border-color: #dc2626;
  color: #fff;
}

.btn-danger:hover:not(:disabled) {
  background: #b91c1c;
}

.danger {
  border-color: #fecaca;
  color: #991b1b;
}

.actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.pill {
  display: inline-block;
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  flex: 0 0 auto;
}

.pill-active {
  background: #ecfdf5;
  color: #065f46;
  border: 1px solid #a7f3d0;
}

.errorBox {
  margin-bottom: 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #991b1b;
  padding: 10px 12px;
  border-radius: 8px;
}

.emptyPanel {
  padding: 18px;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  background: #f8fafc;
  color: #6b7280;
  text-align: center;
}

.muted {
  color: #6b7280;
}

.groupList {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.groupCard {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  padding: 14px;
}

.groupCardHead {
  display: flex;
  align-items: center;
  gap: 12px;
}

.groupAvatar {
  width: 44px;
  height: 44px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  font-size: 14px;
  font-weight: 900;
}

.groupInfo {
  min-width: 0;
  flex: 1 1 auto;
}

.groupName {
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
  line-height: 1.25;
}

.groupMeta {
  color: #64748b;
  font-size: 12px;
  margin-top: 2px;
}

.memberChips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.memberChip {
  display: inline-flex;
  align-items: center;
  padding: 4px 9px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #334155;
  font-size: 11px;
  font-weight: 700;
}

.adminChip {
  background: #e8f0ff;
  color: #173b7a;
}

.groupCardFoot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #eef2f7;
}

.heldDocs {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #e2e8f0;
}

.heldDocsList {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.heldDocsList li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 13px;
}

.heldDocsList a {
  color: #2563eb;
  text-decoration: none;
}

.heldDocsList a:hover {
  text-decoration: underline;
}

.colorSwatches {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.colorSwatch {
  width: 30px;
  height: 30px;
  border-radius: 999px;
  border: 2px solid transparent;
  cursor: pointer;
  padding: 0;
}

.colorSwatch.active {
  border-color: #0f172a;
  box-shadow: 0 0 0 2px #fff inset;
}

.memberBuilder {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 220px;
  overflow: auto;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 8px;
}

.memberBuilderRow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 4px 2px;
}

.memberBuilderName {
  font-size: 13px;
  color: #0f172a;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.adminToggle {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
  color: #334155;
  flex: 0 0 auto;
}

.overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.42);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 14px;
  z-index: 2000;
}

.modal {
  width: 100%;
  max-width: 620px;
  min-width: 0;
  box-sizing: border-box;
  background: #fff;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.24);
}

.modalHead {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
  padding: 14px 16px;
  border-bottom: 1px solid #eee;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.modalTitle {
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
}

.modalSub {
  font-size: 12px;
  color: #6b7280;
  margin-top: 2px;
}

.modalBody {
  padding: 14px 16px;
  max-height: 60vh;
  overflow: auto;
}

.formStack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.modalAlert {
  padding: 12px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.5;
}

.modalAlertDanger {
  border: 1px solid #fecaca;
  background: #fff1f2;
  color: #9f1239;
}

.noticeLine {
  color: #334155;
  font-size: 13px;
}

.modalFoot {
  padding: 14px 16px;
  border-top: 1px solid #eee;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  background: #f8fafc;
}
</style>
