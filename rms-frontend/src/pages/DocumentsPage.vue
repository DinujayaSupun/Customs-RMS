<template>
  <AppLayout>
    <div class="pageHead">
      <h2>Documents</h2>
      <button v-if="canCreate" class="btn btn-primary" @click="goCreate">+ Create Document</button>
    </div>

    <div class="card" style="margin-bottom:14px;">
      <div class="filters">
        <input v-model="q" class="input" placeholder="Search ref/title/company..." />

        <select v-model="status" class="input">
          <option value="">All Status</option>
          <option>PENDING</option>
          <option>IN_PROGRESS</option>
          <option>APPROVED</option>
          <option>REJECTED</option>
          <option>ISSUED</option>
        </select>

        <select v-model="priority" class="input">
          <option value="">All Priority</option>
          <option>LOW</option>
          <option>MEDIUM</option>
          <option>HIGH</option>
          <option>URGENT</option>
        </select>

        <button class="btn" @click="applyNow">Apply</button>
      </div>
    </div>

    <div class="card">
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
            <td colspan="7" class="muted">Loading…</td>
          </tr>

          <tr v-else-if="rows.length === 0">
            <td colspan="7" class="muted">No documents found.</td>
          </tr>

          <tr v-else v-for="d in rows" :key="d.id">
            <td><b>{{ d.refNo }}</b></td>
            <td>{{ d.title }}</td>
            <td>{{ d.companyName }}</td>

            <td><span class="pill" :class="'pill-'+d.priority">{{ d.priority }}</span></td>
            <td><span class="pill" :class="'pill-'+d.status">{{ d.status }}</span></td>

            <td>{{ d.currentOwnerUserId }}</td>

            <td>
              <button
                class="iconBtn"
                :disabled="!canPreview(d)"
                :title="canPreview(d) ? 'Preview' : 'Preview allowed only for DC or current owner'"
                @click="openPreview(d)"
              >
                👁
              </button>

              <button class="btn btn-sm" @click="openDetails(d.id)">Open</button>
            </td>
          </tr>
        </tbody>
      </table>

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
            <div><span class="label">Owner:</span> {{ previewDoc?.currentOwnerUserId }}</div>
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
import { ref, computed, onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import AppLayout from "../layouts/AppLayout.vue";
import { listDocuments } from "../api/documents.api";
import { getCurrentUser } from "../auth/currentUser";

const router = useRouter();

const currentUser = ref(getCurrentUser());
const canCreate = computed(() => ["DC", "PMA"].includes(currentUser.value?.role));

const q = ref("");
const status = ref("");
const priority = ref("");

const loading = ref(false);
const error = ref("");
const all = ref([]);
const rows = ref([]);

const previewOpen = ref(false);
const previewDoc = ref(null);

function canPreview(doc) {
  if (currentUser.value?.role === "DC") return true;
  return doc.currentOwnerUserId === currentUser.value.id;
}

function openPreview(doc) {
  previewDoc.value = doc;
  previewOpen.value = true;
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

function applyNow() {
  rows.value = applyFilters(all.value);
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const data = await listDocuments();
    const list = Array.isArray(data) ? data : (data?.content ?? data?.items ?? []);
    all.value = list;
    rows.value = applyFilters(list);
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
  rows.value = applyFilters(all.value);
}

onMounted(() => {
  window.addEventListener("rms_auth_changed", onUserChanged);
  load();
});

onUnmounted(() => {
  window.removeEventListener("rms_auth_changed", onUserChanged);
});
</script>

<style scoped>
.pageHead { display:flex; align-items:center; justify-content:space-between; margin-bottom:14px; }
h2 { margin:0; }

.card { background:#fff; padding:16px; border-radius:10px; }

.filters { display:grid; grid-template-columns: 1.5fr 1fr 1fr auto; gap:10px; align-items:center; }
.input {
  height:38px; border-radius:8px; border:1px solid #e5e7eb; padding:0 10px; outline:none;
}
.input:focus { border-color:#9ca3af; }

.table { width:100%; border-collapse:collapse; }
.table th, .table td { padding:12px; border-bottom:1px solid #eee; text-align:left; }
.muted { color:#6b7280; padding:20px; }

.btn { padding:10px 12px; border-radius:8px; border:1px solid #e5e7eb; background:#fff; cursor:pointer; }
.btn:hover { background:#f9fafb; }
.btn-primary { background:#2563eb; border-color:#2563eb; color:#fff; }
.btn-primary:hover { background:#1d4ed8; }
.btn-sm { padding:7px 10px; font-size:12px; }

.iconBtn {
  height:32px; width:36px; border-radius:8px; border:1px solid #e5e7eb; background:#fff; cursor:pointer;
  margin-right:8px;
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
  margin-top:12px;
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
</style>