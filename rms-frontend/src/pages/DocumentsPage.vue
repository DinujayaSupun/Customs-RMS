<template>
  <AppLayout>
    <div class="pageHead">
      <div class="titleBlock">
        <h2>Documents</h2>
        <p class="pageSub">Track, filter, and open workflow documents quickly.</p>
      </div>
      <button v-if="canCreate" class="btn btn-primary" @click="goCreate">+ Create Document</button>
    </div>

    <div class="card filtersCard">
      <div class="filters">
        <div class="control">
          <label class="controlLabel">Search</label>
          <input v-model="q" class="input" placeholder="Search ref/title/company..." />
        </div>

        <div class="control">
          <label class="controlLabel">Status</label>
          <select v-model="status" class="input">
            <option value="">All Status</option>
            <option>PENDING</option>
            <option>IN_PROGRESS</option>
            <option>APPROVED</option>
            <option>REJECTED</option>
            <option>ISSUED</option>
          </select>
        </div>

        <div class="control">
          <label class="controlLabel">Priority</label>
          <select v-model="priority" class="input">
            <option value="">All Priority</option>
            <option>LOW</option>
            <option>MEDIUM</option>
            <option>HIGH</option>
            <option>URGENT</option>
          </select>
        </div>

        <div class="control">
          <label class="controlLabel">Sort By</label>
          <select v-model="sortBy" class="input">
            <option value="recent">Most Recent</option>
            <option value="ref_asc">Ref No (A-Z)</option>
            <option value="ref_desc">Ref No (Z-A)</option>
            <option value="title_asc">Title (A-Z)</option>
            <option value="priority_desc">Priority (High-Low)</option>
            <option value="status_asc">Status (Workflow)</option>
          </select>
        </div>

      </div>
    </div>

    <div class="card tableCard">
      <div class="tableHead">
        <div class="tableTitleWrap">
          <span class="tableTitle">Document List</span>
          <span class="tableMeta">{{ rows.length }} result{{ rows.length === 1 ? '' : 's' }}</span>
        </div>
        <span class="tableHint">Latest first by current source order</span>
      </div>

      <div class="tableWrap">
        <table class="table">
          <thead>
            <tr>
              <th>Ref No</th>
              <th>Title</th>
              <th>Company</th>
              <th>Priority</th>
              <th>Status</th>
              <th>Owner</th>
              <th style="width:180px;">Actions</th>
            </tr>
          </thead>

          <tbody>
            <tr v-if="loading">
              <td colspan="7" class="muted">Loading...</td>
            </tr>

            <tr v-else-if="rows.length === 0">
              <td colspan="7" class="muted">No documents found.</td>
            </tr>

            <tr v-else v-for="d in rows" :key="d.id">
              <td class="refCell"><b>{{ d.refNo }}</b></td>
              <td>{{ d.title }}</td>
              <td>{{ d.companyName }}</td>

              <td><span class="pill" :class="'pill-'+d.priority">{{ d.priority }}</span></td>
              <td><span class="pill" :class="'pill-'+d.status">{{ d.status }}</span></td>

              <td class="ownerCell">{{ ownerLabel(d.currentOwnerUserId) }}</td>

              <td>
                <div class="actions">
                <button
                  class="iconBtn"
                  :disabled="!canPreview(d)"
                  :title="canPreview(d) ? 'Preview' : 'Preview allowed only for DC or current owner'"
                  @click="openPreview(d)"
                >
                  View
                </button>

                <button class="btn btn-sm" @click="openDetails(d.id)">Open</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="error" class="errorBox">{{ error }}</div>
    </div>

    <!-- Preview Modal -->
    <div v-if="previewOpen" class="overlay" @click.self="previewOpen=false">
      <div class="modal">
        <div class="modalHead">
          <div>
            <div class="modalTitle">Document Preview</div>
            <div class="modalSub">{{ previewDoc?.refNo }} — {{ previewDoc?.title }}</div>
          </div>
          <button class="iconBtn" @click="previewOpen=false">✕</button>
        </div>

        <div class="modalBody">
          <div class="grid">
            <div><span class="label">Company:</span> {{ previewDoc?.companyName }}</div>
            <div><span class="label">Received:</span> {{ previewDoc?.receivedDate }}</div>
            <div><span class="label">Priority:</span> {{ previewDoc?.priority }}</div>
            <div><span class="label">Status:</span> {{ previewDoc?.status }}</div>
            <div><span class="label">Owner:</span> {{ ownerLabel(previewDoc?.currentOwnerUserId) }}</div>
          </div>

          <div class="note">
            This is a quick review. For full workflow actions, open the document details page.
          </div>
        </div>

        <div class="modalFoot">
          <button class="btn" @click="previewOpen=false">Close</button>
          <button class="btn btn-primary" @click="openDetails(previewDoc.id)">Open Full Document</button>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from "vue";
import { useRouter } from "vue-router";
import AppLayout from "../layouts/AppLayout.vue";
import { listDocuments } from "../api/documents.api";
import { listUsers } from "../api/auth.api";
import { getCurrentUser } from "../auth/currentUser";
import { formatUserLabelById } from "../auth/userLabel";

const router = useRouter();

const currentUser = ref(getCurrentUser());
const canCreate = computed(() => ["DC", "PMA"].includes(currentUser.value?.role));

const q = ref("");
const status = ref("");
const priority = ref("");
const sortBy = ref("recent");

const loading = ref(false);
const error = ref("");
const all = ref([]);
const rows = ref([]);
const users = ref([]);

const previewOpen = ref(false);
const previewDoc = ref(null);

const PRIORITY_ORDER = { LOW: 1, MEDIUM: 2, HIGH: 3, URGENT: 4 };
const STATUS_ORDER = { PENDING: 1, IN_PROGRESS: 2, APPROVED: 3, ISSUED: 4, REJECTED: 5 };

function canPreview(doc) {
  if (currentUser.value?.role === "DC") return true;
  return doc.currentOwnerUserId === currentUser.value.id;
}

function openPreview(doc) {
  previewDoc.value = doc;
  previewOpen.value = true;
}

function ownerLabel(userId) {
  return formatUserLabelById(userId, users.value);
}

function openDetails(id) {
  previewOpen.value = false;
  router.push(`/documents/${id}`);
}

function goCreate() {
  router.push("/documents/new");
}

function applyFilters(list) {
  const qq = q.value.trim().toLowerCase();
  return list.filter((d) => {
    const matchQ =
      !qq ||
      String(d.refNo ?? "").toLowerCase().includes(qq) ||
      String(d.title ?? "").toLowerCase().includes(qq) ||
      String(d.companyName ?? "").toLowerCase().includes(qq);

    const matchStatus = !status.value || d.status === status.value;
    const matchPriority = !priority.value || d.priority === priority.value;

    return matchQ && matchStatus && matchPriority;
  });
}

function toText(value) {
  return String(value ?? "").trim().toLowerCase();
}

function toRecentScore(doc) {
  const source = doc.updatedAt ?? doc.receivedDate ?? doc.createdAt;
  const parsed = Date.parse(source);
  if (!Number.isNaN(parsed)) return parsed;
  const idNumber = Number(doc.id);
  return Number.isFinite(idNumber) ? idNumber : 0;
}

function sortDocuments(list) {
  const arr = [...list];
  switch (sortBy.value) {
    case "ref_asc":
      return arr.sort((a, b) => toText(a.refNo).localeCompare(toText(b.refNo)));
    case "ref_desc":
      return arr.sort((a, b) => toText(b.refNo).localeCompare(toText(a.refNo)));
    case "title_asc":
      return arr.sort((a, b) => toText(a.title).localeCompare(toText(b.title)));
    case "priority_desc":
      return arr.sort((a, b) => (PRIORITY_ORDER[b.priority] ?? 0) - (PRIORITY_ORDER[a.priority] ?? 0));
    case "status_asc":
      return arr.sort((a, b) => (STATUS_ORDER[a.status] ?? 999) - (STATUS_ORDER[b.status] ?? 999));
    case "recent":
    default:
      return arr.sort((a, b) => toRecentScore(b) - toRecentScore(a));
  }
}

function applyNow() {
  rows.value = sortDocuments(applyFilters(all.value));
}

watch([q, status, priority, sortBy], () => {
  applyNow();
});

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const data = await listDocuments();
    const list = Array.isArray(data) ? data : (data?.content ?? data?.items ?? []);
    all.value = list;
    applyNow();
  } catch (e) {
    error.value = e?.message ?? "Failed to load documents";
    all.value = [];
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function onUserChanged() {
  currentUser.value = getCurrentUser();
  applyNow();
}

onMounted(() => {
  window.addEventListener("rms_auth_changed", onUserChanged);
  listUsers().then((data) => {
    users.value = data || [];
  }).catch(() => {
    users.value = [];
  });
  load();
});

onUnmounted(() => {
  window.removeEventListener("rms_auth_changed", onUserChanged);
});
</script>

<style scoped>
.pageHead {
  display:flex;
  align-items:flex-start;
  justify-content:space-between;
  gap:12px;
  margin-bottom:14px;
}
h2 { margin:0; line-height:1.15; }
.titleBlock { display:flex; flex-direction:column; gap:4px; }
.pageSub { margin:0; color:#6b7280; font-size:12px; }

.card {
  background:#fff;
  padding:16px;
  border-radius:14px;
  border:1px solid #e5e7eb;
  box-shadow:0 6px 18px rgba(17, 24, 39, 0.05);
}
.filtersCard { margin-bottom:14px; }

.filters {
  display:grid;
  grid-template-columns: 1.6fr 1fr 1fr 1fr;
  gap:12px;
  align-items:end;
}
.control { display:flex; flex-direction:column; gap:6px; }
.controlLabel { font-size:12px; font-weight:700; color:#374151; }
.input {
  height:40px;
  border-radius:10px;
  border:1px solid #e5e7eb;
  padding:0 12px;
  outline:none;
  background:#fff;
  font-size:13px;
}

.input:hover { border-color:#d1d5db; }
.input:focus { border-color:#9ca3af; box-shadow:0 0 0 3px rgba(229, 231, 235, 0.9); }

.tableCard { padding:0; overflow:hidden; }
.tableHead {
  padding:14px 16px;
  border-bottom:1px solid #e5e7eb;
  display:flex;
  justify-content:space-between;
  align-items:center;
  background:linear-gradient(180deg, #ffffff 0%, #f9fafb 100%);
}
.tableTitleWrap { display:flex; align-items:baseline; gap:10px; }
.tableTitle { font-size:14px; font-weight:800; color:#111827; }
.tableMeta { font-size:12px; color:#6b7280; }
.tableHint { font-size:12px; color:#9ca3af; }

.tableWrap { overflow:auto; }

.table {
  width:100%;
  border-collapse:separate;
  border-spacing:0;
  min-width:960px;
}
.table th,
.table td {
  padding:14px 14px;
  border-bottom:1px solid #eef2f7;
  text-align:left;
  white-space:nowrap;
  font-size:14px;
}
.table th {
  position:sticky;
  top:0;
  z-index:1;
  background:#f9fafb;
  font-size:12px;
  letter-spacing:0.05em;
  text-transform:uppercase;
  color:#6b7280;
  font-weight:800;
}
.table tbody tr {
  transition:background-color 0.16s ease;
}
.table tbody tr:hover { background:#f8fafc; }
.refCell {
  font-size:16px;
  font-weight:800;
  color:#111827;
  font-variant-numeric:tabular-nums;
}
.ownerCell {
  font-weight:600;
  color:#1f2937;
}
.muted { color:#6b7280; padding:20px; }

.btn { padding:10px 12px; border-radius:10px; border:1px solid #e5e7eb; background:#fff; cursor:pointer; }
.btn:hover { background:#f9fafb; }
.btn-primary { background:#2563eb; border-color:#2563eb; color:#fff; }
.btn-primary:hover { background:#1d4ed8; }
.btn-sm { padding:8px 12px; font-size:12px; font-weight:700; }

.actions {
  display:inline-flex;
  align-items:center;
  gap:8px;
}

.iconBtn {
  height:34px;
  min-width:54px;
  border-radius:10px;
  border:1px solid #e5e7eb;
  background:#fff;
  cursor:pointer;
  font-size:12px;
  font-weight:700;
  padding:0 10px;
}
.iconBtn:hover { background:#f9fafb; }
.iconBtn:disabled { opacity:0.45; cursor:not-allowed; }

.pill {
  display:inline-block;
  padding:4px 10px;
  border-radius:999px;
  font-size:12px;
  font-weight:700;
  border:1px solid transparent;
}
.pill-LOW { background:#f3f4f6; color:#374151; border-color:#e5e7eb; }
.pill-MEDIUM { background:#eef2ff; color:#3730a3; border-color:#c7d2fe; }
.pill-HIGH { background:#fff7ed; color:#9a3412; border-color:#fed7aa; }
.pill-URGENT { background:#fef2f2; color:#991b1b; border-color:#fecaca; }

.pill-PENDING { background:#f3f4f6; color:#374151; border-color:#e5e7eb; }
.pill-IN_PROGRESS { background:#eff6ff; color:#1d4ed8; border-color:#bfdbfe; }
.pill-APPROVED { background:#ecfdf5; color:#047857; border-color:#a7f3d0; }
.pill-REJECTED { background:#fef2f2; color:#b91c1c; border-color:#fecaca; }
.pill-ISSUED { background:#fffbeb; color:#92400e; border-color:#fde68a; }

.errorBox {
  margin:12px 16px 16px;
  background:#fef2f2;
  border:1px solid #fecaca;
  color:#991b1b;
  padding:10px 12px;
  border-radius:8px;
}

/* Modal */
.overlay {
  position:fixed; inset:0;
  background:rgba(0,0,0,0.4);
  display:flex; align-items:center; justify-content:center;
  padding:14px;
}
.modal {
  width:100%;
  max-width:700px;
  background:#fff;
  border-radius:10px;
  overflow:hidden;
}
.modalHead {
  display:flex; align-items:flex-start; justify-content:space-between;
  padding:14px 16px;
  border-bottom:1px solid #eee;
}
.modalTitle { font-weight:800; font-size:14px; }
.modalSub { font-size:12px; color:#6b7280; margin-top:2px; }
.modalBody { padding:16px; }
.modalFoot {
  display:flex; justify-content:flex-end; gap:10px;
  padding:14px 16px;
  border-top:1px solid #eee;
}
.grid { display:grid; grid-template-columns:1fr 1fr; gap:10px; font-size:13px; }
.label { font-weight:700; color:#374151; }
.note {
  margin-top:14px;
  font-size:12px;
  color:#6b7280;
  background:#f9fafb;
  border:1px solid #e5e7eb;
  padding:10px 12px;
  border-radius:8px;
}

@media (max-width: 960px) {
  .filters {
    grid-template-columns:1fr 1fr;
    align-items:stretch;
  }

  .tableHead {
    flex-direction:column;
    align-items:flex-start;
    gap:4px;
  }
}

@media (max-width: 640px) {
  .pageHead {
    flex-direction:column;
    align-items:stretch;
  }

  .filters {
    grid-template-columns:1fr;
  }

  .btn,
  .btn-sm,
  .iconBtn {
    min-height:36px;
  }
}
</style>