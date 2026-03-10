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
        <span class="tableHint">{{ sortHint }}</span>
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
              <td class="refCell">
                <div class="refWrap">
                  <span
                    class="docTypeBadge"
                    :class="'docType-' + docTypeClass(d.mainAttachmentType)"
                    :title="attachmentTypeLabel(d.mainAttachmentType)"
                  >
                    <component
                      :is="attachmentIconComponent(d.mainAttachmentType)"
                      class="docIcon"
                      aria-hidden="true"
                    />
                  </span>
                  <b>{{ d.refNo }}</b>
                </div>
              </td>
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
                  :aria-label="canPreview(d) ? 'Preview document' : 'Preview not allowed'"
                  @click="openPreview(d)"
                >
                  <Eye class="actionIcon" aria-hidden="true" />
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
          <div class="previewPills">
            <span class="pill" :class="'pill-'+previewDoc?.status">{{ previewDoc?.status || '-' }}</span>
            <span class="pill" :class="'pill-'+previewDoc?.priority">{{ previewDoc?.priority || '-' }}</span>
            <span class="pill pill-PENDING">Owner: {{ ownerLabel(previewDoc?.currentOwnerUserId) }}</span>
          </div>

          <div class="previewGrid">
            <div><span class="label">Company:</span> {{ previewDoc?.companyName || '-' }}</div>
            <div><span class="label">Received:</span> {{ formatDateSafe(previewDoc?.receivedDate) }}</div>
            <div><span class="label">Days Open:</span> {{ previewDaysOpen }}</div>
            <div><span class="label">Main File Type:</span> {{ previewMainAttachmentType }}</div>
            <div><span class="label">Attachments:</span> {{ previewAttachmentCount }}</div>
            <div><span class="label">Main File Previewable:</span> {{ previewIsMainFilePreviewable ? 'Yes' : 'No' }}</div>
          </div>

          <div v-if="previewLoadingExtras" class="note">Loading additional preview details...</div>
          <div v-else-if="previewExtrasError" class="note noteWarn">{{ previewExtrasError }}</div>

          <div v-if="previewCanSeeOperational" class="opsCard">
            <div class="opsTitle">Latest Activity</div>
            <div class="opsRow">
              <span class="label">Last Action:</span>
              <span>
                {{ previewLastMovement?.actionType || '-' }}
                <span v-if="previewLastMovement">• {{ formatDateTimeSafe(previewLastMovement.actionAt) }}</span>
              </span>
            </div>
            <div class="opsRow">
              <span class="label">Action By:</span>
              <span>{{ previewLastMovement ? ownerLabel(previewLastMovement.actionByUserId) : '-' }}</span>
            </div>
            <div class="opsRow">
              <span class="label">Latest Remark:</span>
              <span class="remarkPreview">{{ previewLastRemark?.remarkText || 'No remarks yet.' }}</span>
            </div>
          </div>

          <div class="note">
            Preview shows a quick decision summary. Open full document for complete workflow, files, and timeline.
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
import { File, FileText, FileSpreadsheet, Image, Archive, Eye } from "lucide-vue-next";
import AppLayout from "../layouts/AppLayout.vue";
import { listDocuments, listMovements, listRemarks, listAttachments } from "../api/documents.api";
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

const sortHint = computed(() => {
  switch (sortBy.value) {
    case "ref_asc":
      return "Sorted by Ref No (A-Z)";
    case "ref_desc":
      return "Sorted by Ref No (Z-A)";
    case "title_asc":
      return "Sorted by Title (A-Z)";
    case "priority_desc":
      return "Sorted by Priority (High-Low)";
    case "status_asc":
      return "Sorted by Status (Workflow)";
    case "recent":
    default:
      return "Sorted by Most Recent";
  }
});

const loading = ref(false);
const error = ref("");
const all = ref([]);
const rows = ref([]);
const users = ref([]);

const previewOpen = ref(false);
const previewDoc = ref(null);
const previewLoadingExtras = ref(false);
const previewExtrasError = ref("");
const previewMovements = ref([]);
const previewRemarks = ref([]);
const previewAttachments = ref([]);

const PRIORITY_ORDER = { LOW: 1, MEDIUM: 2, HIGH: 3, URGENT: 4 };
const STATUS_ORDER = { PENDING: 1, IN_PROGRESS: 2, APPROVED: 3, ISSUED: 4, REJECTED: 5 };

function canPreview(doc) {
  if (currentUser.value?.role === "DC") return true;
  return doc.currentOwnerUserId === currentUser.value.id;
}

function openPreview(doc) {
  previewDoc.value = doc;
  previewOpen.value = true;
  resetPreviewExtras();
  void loadPreviewExtras(doc);
}

function resetPreviewExtras() {
  previewLoadingExtras.value = false;
  previewExtrasError.value = "";
  previewMovements.value = [];
  previewRemarks.value = [];
  previewAttachments.value = [];
}

async function loadPreviewExtras(doc) {
  if (!doc?.id || !canPreview(doc)) return;

  previewLoadingExtras.value = true;
  previewExtrasError.value = "";

  const [movRes, remRes, attRes] = await Promise.allSettled([
    listMovements(doc.id),
    listRemarks(doc.id),
    listAttachments(doc.id),
  ]);

  if (movRes.status === "fulfilled") {
    previewMovements.value = Array.isArray(movRes.value) ? movRes.value : [];
  }
  if (remRes.status === "fulfilled") {
    previewRemarks.value = Array.isArray(remRes.value) ? remRes.value : [];
  }
  if (attRes.status === "fulfilled") {
    previewAttachments.value = Array.isArray(attRes.value) ? attRes.value : [];
  }

  if (movRes.status === "rejected" || remRes.status === "rejected" || attRes.status === "rejected") {
    previewExtrasError.value = "Some preview details could not be loaded with your current access.";
  }

  previewLoadingExtras.value = false;
}

const previewIsOwner = computed(() => {
  if (!previewDoc.value || !currentUser.value) return false;
  return Number(previewDoc.value.currentOwnerUserId) === Number(currentUser.value.id);
});

const previewCanSeeOperational = computed(() => {
  if (!previewDoc.value || !currentUser.value) return false;
  return currentUser.value.role === "DC" || previewIsOwner.value;
});

const previewLastMovement = computed(() => {
  if (!previewMovements.value.length) return null;
  return [...previewMovements.value].sort((a, b) => {
    const ta = Date.parse(a?.actionAt ?? "");
    const tb = Date.parse(b?.actionAt ?? "");
    return (Number.isNaN(tb) ? 0 : tb) - (Number.isNaN(ta) ? 0 : ta);
  })[0];
});

const previewLastRemark = computed(() => {
  if (!previewRemarks.value.length) return null;
  return [...previewRemarks.value].sort((a, b) => {
    const ta = Date.parse(a?.remarkedAt ?? "");
    const tb = Date.parse(b?.remarkedAt ?? "");
    return (Number.isNaN(tb) ? 0 : tb) - (Number.isNaN(ta) ? 0 : ta);
  })[0];
});

const previewMainAttachment = computed(() => {
  if (!previewAttachments.value.length) return null;
  const sorted = [...previewAttachments.value].sort((a, b) => Number(a.versionNo) - Number(b.versionNo));
  return sorted.find((a) => Number(a.versionNo) === 1) || sorted[0];
});

const previewAttachmentCount = computed(() => previewAttachments.value.length);

const previewMainAttachmentType = computed(() => {
  if (previewMainAttachment.value?.fileName) {
    return resolveAttachmentTypeFromName(previewMainAttachment.value.fileName);
  }
  return docTypeClass(previewDoc.value?.mainAttachmentType);
});

const previewIsMainFilePreviewable = computed(() => {
  const name = previewMainAttachment.value?.fileName;
  if (!name) return false;
  const n = String(name).toLowerCase();
  return n.endsWith(".pdf") || n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif") || n.endsWith(".webp");
});

const previewDaysOpen = computed(() => {
  const raw = previewDoc.value?.receivedDate;
  if (!raw) return "-";
  const dt = new Date(raw);
  if (Number.isNaN(dt.getTime())) return "-";
  const dayMs = 24 * 60 * 60 * 1000;
  return Math.max(0, Math.floor((Date.now() - dt.getTime()) / dayMs));
});

function resolveAttachmentTypeFromName(fileName) {
  const lower = String(fileName ?? "").toLowerCase();
  if (lower.endsWith(".pdf")) return "PDF";
  if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "DOC";
  if (lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv")) return "XLS";
  if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp")) return "IMG";
  if (lower.endsWith(".txt")) return "TXT";
  if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")) return "ZIP";
  return "FILE";
}

function formatDateSafe(value) {
  if (!value) return "-";
  const dt = new Date(value);
  if (Number.isNaN(dt.getTime())) return String(value);
  return dt.toLocaleDateString();
}

function formatDateTimeSafe(value) {
  if (!value) return "-";
  const dt = new Date(value);
  if (Number.isNaN(dt.getTime())) return String(value);
  return dt.toLocaleString();
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

function docTypeClass(type) {
  const t = String(type ?? "FILE").toUpperCase();
  if (["PDF", "DOC", "XLS", "IMG", "TXT", "ZIP"].includes(t)) return t;
  return "FILE";
}

function attachmentIconComponent(type) {
  switch (docTypeClass(type)) {
    case "PDF":
    case "DOC":
    case "TXT":
      return FileText;
    case "XLS":
      return FileSpreadsheet;
    case "IMG":
      return Image;
    case "ZIP":
      return Archive;
    case "FILE":
    default:
      return File;
  }
}

function attachmentTypeLabel(type) {
  return `Main attachment type: ${docTypeClass(type)}`;
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

.tableCard {
  padding:0;
  overflow:hidden;
  position:relative;
  z-index:0;
  isolation:isolate;
}
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

.tableWrap {
  overflow:auto;
  position:relative;
  z-index:0;
}

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
.refWrap {
  display:flex;
  align-items:center;
  gap:8px;
}
.docTypeBadge {
  display:inline-flex;
  align-items:center;
  justify-content:center;
  width:28px;
  height:24px;
  padding:0;
  border-radius:999px;
  border:1px solid #d1d5db;
  background:#f9fafb;
  color:#374151;
  font-size:10px;
  font-weight:800;
  letter-spacing:0.03em;
}
.docIcon {
  width:14px;
  height:14px;
  stroke-width:2.1;
}
.docType-PDF { background:#fef2f2; border-color:#fecaca; color:#b91c1c; }
.docType-DOC { background:#eff6ff; border-color:#bfdbfe; color:#1d4ed8; }
.docType-XLS { background:#ecfdf5; border-color:#a7f3d0; color:#047857; }
.docType-IMG { background:#fff7ed; border-color:#fed7aa; color:#9a3412; }
.docType-TXT { background:#eef2ff; border-color:#c7d2fe; color:#3730a3; }
.docType-ZIP { background:#fffbeb; border-color:#fde68a; color:#92400e; }
.docType-FILE { background:#f3f4f6; border-color:#e5e7eb; color:#4b5563; }
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
  min-width:40px;
  border-radius:10px;
  border:1px solid #e5e7eb;
  background:#fff;
  cursor:pointer;
  padding:0;
  display:inline-flex;
  align-items:center;
  justify-content:center;
}
.iconBtn:hover { background:#f9fafb; }
.iconBtn:disabled { opacity:0.45; cursor:not-allowed; }
.actionIcon {
  width:14px;
  height:14px;
  stroke-width:2.2;
}

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
  z-index:3000;
  background:rgba(0,0,0,0.4);
  display:flex; align-items:center; justify-content:center;
  padding:14px;
}
.modal {
  position:relative;
  z-index:3001;
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
.previewPills {
  display:flex;
  gap:8px;
  flex-wrap:wrap;
  margin-bottom:12px;
}
.previewGrid {
  display:grid;
  grid-template-columns:1fr 1fr;
  gap:10px;
  font-size:13px;
}
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
.noteWarn {
  background:#fffbeb;
  border-color:#fde68a;
  color:#92400e;
}
.opsCard {
  margin-top:14px;
  border:1px solid #e5e7eb;
  border-radius:10px;
  padding:12px;
  background:#ffffff;
}
.opsTitle {
  font-size:12px;
  font-weight:800;
  margin-bottom:8px;
  color:#111827;
  text-transform:uppercase;
  letter-spacing:0.03em;
}
.opsRow {
  display:grid;
  grid-template-columns:120px 1fr;
  gap:10px;
  font-size:13px;
  margin-bottom:6px;
}
.opsRow:last-child { margin-bottom:0; }
.remarkPreview {
  display:-webkit-box;
  -webkit-line-clamp:2;
  -webkit-box-orient:vertical;
  overflow:hidden;
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

  .previewGrid {
    grid-template-columns:1fr;
  }

  .opsRow {
    grid-template-columns:1fr;
    gap:2px;
  }

  .btn,
  .btn-sm,
  .iconBtn {
    min-height:36px;
  }
}
</style>