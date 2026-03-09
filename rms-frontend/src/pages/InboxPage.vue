<template>
  <AppLayout>
    <div class="pageHead">
      <div class="titleBlock">
        <h2>My Inbox</h2>
        <p class="pageSub">Items currently assigned to you and awaiting action.</p>
      </div>
      <div class="headActions">
        <select v-model="sortBy" class="input sortInput">
          <option value="recent">Most Recent</option>
          <option value="ref_asc">Ref No (A-Z)</option>
          <option value="ref_desc">Ref No (Z-A)</option>
          <option value="title_asc">Title (A-Z)</option>
          <option value="priority_desc">Priority (High-Low)</option>
          <option value="status_asc">Status (Workflow)</option>
        </select>
        <button class="btn" @click="load">Refresh</button>
      </div>
    </div>

    <div v-if="error" class="errorBox">{{ error }}</div>

    <div class="card tableCard">
      <div class="tableHead">
        <div class="tableTitleWrap">
          <span class="tableTitle">Inbox Documents</span>
          <span class="tableMeta">{{ rows.length }} item{{ rows.length === 1 ? '' : 's' }}</span>
        </div>
        <span class="tableHint">Only active assignments are shown</span>
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
              <th style="width:120px;">Open</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="6" class="muted">Loading...</td>
            </tr>
            <tr v-else-if="rows.length===0">
              <td colspan="6" class="muted">No inbox items.</td>
            </tr>
            <tr v-else v-for="d in rows" :key="d.id">
              <td class="refCell"><b>{{ d.refNo }}</b></td>
              <td>{{ d.title }}</td>
              <td>{{ d.companyName }}</td>
              <td><span class="pill" :class="'pill-'+d.priority">{{ d.priority }}</span></td>
              <td><span class="pill" :class="'pill-'+d.status">{{ d.status }}</span></td>
              <td>
                <div class="actions">
                  <button class="btn btn-sm" @click="open(d.id)">Open</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from "vue";
import { useRouter } from "vue-router";
import AppLayout from "../layouts/AppLayout.vue";
import { listDocuments } from "../api/documents.api";
import { getCurrentUser } from "../auth/currentUser";

const router = useRouter();

const loading = ref(false);
const error = ref("");
const allRows = ref([]);
const rows = ref([]);
const sortBy = ref("recent");

const PRIORITY_ORDER = { LOW: 1, MEDIUM: 2, HIGH: 3, URGENT: 4 };
const STATUS_ORDER = { PENDING: 1, IN_PROGRESS: 2, APPROVED: 3, ISSUED: 4, REJECTED: 5 };

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
  rows.value = sortDocuments(allRows.value);
}

watch(sortBy, () => {
  applyNow();
});

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const user = getCurrentUser();
    const data = await listDocuments();
    const list = Array.isArray(data) ? data : (data?.content ?? data?.items ?? []);
    allRows.value = list.filter((d) => d.currentOwnerUserId === user.id && d.status !== "ISSUED");
    applyNow();
  } catch (e) {
    error.value = e?.message ?? "Failed to load inbox";
    allRows.value = [];
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function open(id) {
  router.push(`/documents/${id}`);
}

onMounted(() => {
  window.addEventListener("rms_auth_changed", load);
  load();
});

onUnmounted(() => {
  window.removeEventListener("rms_auth_changed", load);
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
.headActions { display:flex; align-items:center; gap:8px; }
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
.sortInput { min-width:200px; }

.card {
  background:#fff;
  padding:16px;
  border-radius:14px;
  border:1px solid #e5e7eb;
  box-shadow:0 6px 18px rgba(17, 24, 39, 0.05);
}

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
  min-width:820px;
}
.table th, .table td {
  padding:14px;
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
.table tbody tr { transition:background-color 0.16s ease; }
.table tbody tr:hover { background:#f8fafc; }
.refCell {
  font-size:16px;
  font-weight:800;
  color:#111827;
  font-variant-numeric:tabular-nums;
}
.muted { color:#6b7280; padding:20px; }

.btn { padding:10px 12px; border-radius:10px; border:1px solid #e5e7eb; background:#fff; cursor:pointer; }
.btn:hover { background:#f9fafb; }
.btn-sm { padding:8px 12px; font-size:12px; font-weight:700; }

.actions {
  display:inline-flex;
  align-items:center;
}

.pill {
  display:inline-block; padding:4px 10px; border-radius:999px; font-size:12px; font-weight:700;
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
  margin-bottom:12px;
  background:#fef2f2; border:1px solid #fecaca; color:#991b1b;
  padding:10px 12px; border-radius:8px;
}

@media (max-width: 900px) {
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

  .headActions {
    flex-direction:column;
    align-items:stretch;
  }

  .sortInput {
    min-width:0;
    width:100%;
  }

  .btn,
  .btn-sm {
    min-height:36px;
  }
}
</style>