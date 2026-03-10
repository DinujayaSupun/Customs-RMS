<template>
  <AppLayout>
    <div class="pageHead">
      <div>
        <h2>Audit Logs</h2>
        <p class="pageSub">All important system actions with filters and CSV export.</p>
      </div>
      <button class="btn" :disabled="loading" @click="downloadCsv">Export CSV</button>
    </div>

    <div v-if="!canViewLogs" class="errorBox">Only DC and ADMIN can access logs.</div>

    <div v-else class="card">
      <div class="filters">
        <div class="control">
          <label>From</label>
          <input v-model="fromDate" type="date" class="input" />
        </div>

        <div class="control">
          <label>To</label>
          <input v-model="toDate" type="date" class="input" />
        </div>

        <div class="control">
          <label>Action</label>
          <select v-model="actionType" class="input">
            <option value="">All Actions</option>
            <option v-for="a in actionOptions" :key="a" :value="a">{{ a }}</option>
          </select>
        </div>

        <div class="control controlGrow">
          <label>Document Ref / ID</label>
          <input v-model="documentFilter" class="input" placeholder="ex: REF-2026-001 or 42" />
        </div>

        <div class="control controlGrow">
          <label>Performed By</label>
          <select v-model="performedByUserId" class="input">
            <option value="">All Users</option>
            <option
              v-for="u in performedByOptions"
              :key="`performer-${u.id}`"
              :value="String(u.id)"
            >
              {{ u.name }} (ID {{ u.id }})
            </option>
          </select>
        </div>
      </div>

      <div class="filterActions">
        <button class="btn btn-primary" :disabled="loading" @click="applyFilters">Apply</button>
        <button class="btn" :disabled="loading" @click="resetFilters">Reset</button>
      </div>

      <div v-if="error" class="errorBox">{{ error }}</div>

      <div class="tableWrap">
        <table class="table">
          <thead>
            <tr>
              <th>Performed At</th>
              <th>Action</th>
              <th>Entity</th>
              <th>Entity ID</th>
              <th>Document Ref</th>
              <th>Performed By</th>
              <th>Message</th>
              <th>View</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="8" class="muted">Loading logs...</td>
            </tr>
            <tr v-else-if="rows.length === 0">
              <td colspan="8" class="muted">No logs found for current filters.</td>
            </tr>
            <tr v-else v-for="row in rows" :key="row.id">
              <td class="mono">{{ formatDateTime(row.performedAt) }}</td>
              <td><span class="pill">{{ row.actionType || "-" }}</span></td>
              <td>{{ row.entityType || "-" }}</td>
              <td>{{ row.entityId ?? "-" }}</td>
              <td>{{ row.documentRef || "-" }}</td>
              <td>{{ row.performedByUserName || `User ID ${row.performedByUserId}` }}</td>
              <td>
                <div>{{ row.message || "-" }}</div>
                <div v-if="row.detailsJson" class="small mono">{{ row.detailsJson }}</div>
              </td>
              <td>
                <button class="btn btn-sm" @click="openView(row)">View</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pager">
        <button class="btn btn-sm" :disabled="page === 0 || loading" @click="prevPage">Prev</button>
        <span>Page {{ page + 1 }} of {{ totalPagesDisplay }}</span>
        <button class="btn btn-sm" :disabled="last || loading" @click="nextPage">Next</button>

        <select v-model.number="size" class="input sizeSelect" @change="changeSize">
          <option :value="20">20</option>
          <option :value="50">50</option>
          <option :value="100">100</option>
        </select>
      </div>
    </div>

    <div v-if="selectedLog" class="overlay" @click.self="closeView">
      <div class="modal">
        <div class="modalHead">
          <div>
            <div class="modalTitle">Log Details</div>
            <div class="modalSub">ID {{ selectedLog.id }} • {{ selectedLog.actionType || "-" }}</div>
          </div>
          <button class="btn btn-sm" @click="closeView">Close</button>
        </div>

        <div class="modalBody">
          <div class="kv"><span class="k">Performed At</span><span class="v">{{ formatDateTime(selectedLog.performedAt) }}</span></div>
          <div class="kv"><span class="k">Action</span><span class="v">{{ selectedLog.actionType || "-" }}</span></div>
          <div class="kv"><span class="k">Entity Type</span><span class="v">{{ selectedLog.entityType || "-" }}</span></div>
          <div class="kv"><span class="k">Entity ID</span><span class="v">{{ selectedLog.entityId ?? "-" }}</span></div>
          <div class="kv"><span class="k">Document Ref</span><span class="v">{{ selectedLog.documentRef || "-" }}</span></div>
          <div class="kv"><span class="k">Performed By</span><span class="v">{{ selectedLog.performedByUserName || `User ID ${selectedLog.performedByUserId}` }}</span></div>
          <div class="kv"><span class="k">Message</span><span class="v">{{ selectedLog.message || "-" }}</span></div>

          <div class="detailsBlock">
            <div class="k">Details</div>
            <div v-if="readableDetails.length" class="docGrid">
              <div v-for="item in readableDetails" :key="item.key" class="kv">
                <span class="k">{{ item.label }}</span>
                <span class="v">{{ item.value }}</span>
              </div>
            </div>
            <div v-else class="small">No additional details.</div>

            <button class="btn btn-sm rawToggle" @click="showRawJson = !showRawJson">
              {{ showRawJson ? "Hide raw JSON" : "Show raw JSON" }}
            </button>

            <pre v-if="showRawJson" class="jsonBox">{{ formattedDetailsJson }}</pre>
          </div>

          <div class="detailsBlock">
            <div class="k">Document Details</div>
            <div v-if="documentLoading" class="small">Loading document details...</div>
            <div v-else-if="documentError" class="small docError">{{ documentError }}</div>
            <div v-else-if="selectedDocument" class="docGrid">
              <div class="kv"><span class="k">Ref No</span><span class="v">{{ selectedDocument.refNo || "-" }}</span></div>
              <div class="kv"><span class="k">Title</span><span class="v">{{ selectedDocument.title || "-" }}</span></div>
              <div class="kv"><span class="k">Company</span><span class="v">{{ selectedDocument.companyName || "-" }}</span></div>
              <div class="kv"><span class="k">Status</span><span class="v">{{ selectedDocument.status || "-" }}</span></div>
              <div class="kv"><span class="k">Priority</span><span class="v">{{ selectedDocument.priority || "-" }}</span></div>
              <div class="kv"><span class="k">Received Date</span><span class="v">{{ selectedDocument.receivedDate || "-" }}</span></div>
              <div class="kv"><span class="k">Created By ID</span><span class="v">{{ selectedDocument.createdByUserId ?? "-" }}</span></div>
              <div class="kv"><span class="k">Current Owner ID</span><span class="v">{{ selectedDocument.currentOwnerUserId ?? "-" }}</span></div>
              <div class="kv"><span class="k">Created At</span><span class="v">{{ formatDateTime(selectedDocument.createdAt) }}</span></div>
              <div class="kv"><span class="k">Completed At</span><span class="v">{{ formatDateTime(selectedDocument.completedAt) }}</span></div>
              <div class="kv"><span class="k">Issued At</span><span class="v">{{ formatDateTime(selectedDocument.issuedAt) }}</span></div>
            </div>
            <div v-else class="small">No document linked for this log entry.</div>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import AppLayout from "../layouts/AppLayout.vue";
import { getCurrentUser } from "../auth/currentUser";
import { getDocument } from "../api/documents.api";
import { buildAuditLogsExportUrl, listAuditLogs } from "../api/logs.api";

const currentUser = ref(getCurrentUser());
const canViewLogs = computed(() => ["ADMIN", "DC"].includes(currentUser.value?.role));

const loading = ref(false);
const error = ref("");
const rows = ref([]);

const page = ref(0);
const size = ref(20);
const last = ref(true);
const totalPages = ref(0);

const fromDate = ref("");
const toDate = ref("");
const actionType = ref("");
const documentFilter = ref("");
const performedByUserId = ref("");
const selectedLog = ref(null);
const selectedDocument = ref(null);
const documentLoading = ref(false);
const documentError = ref("");
const showRawJson = ref(false);

const actionOptions = [
  "CREATE",
  "UPDATE",
  "DELETE",
  "FORWARD",
  "RETURN",
  "APPROVE",
  "REJECT",
  "ISSUE",
  "REOPEN",
  "UPLOAD",
  "DOWNLOAD",
  "REMARK",
  "LOGIN_SUCCESS",
  "LOGIN_FAILED",
  "USER_CREATE",
  "USER_UPDATE",
  "USER_ACTIVATE",
  "USER_DEACTIVATE",
  "USER_RESET_PASSWORD",
  "USER_MERGE",
];

const performedByOptions = computed(() => {
  const unique = new Map();
  for (const row of rows.value) {
    if (!row?.performedByUserId) continue;
    unique.set(row.performedByUserId, {
      id: row.performedByUserId,
      name: row.performedByUserName || "Unknown",
    });
  }
  return [...unique.values()].sort((a, b) => String(a.name).localeCompare(String(b.name)));
});

const totalPagesDisplay = computed(() => Math.max(totalPages.value, 1));

const formattedDetailsJson = computed(() => {
  const raw = selectedLog.value?.detailsJson;
  if (!raw) return "-";
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
});

const readableDetails = computed(() => {
  const raw = selectedLog.value?.detailsJson;
  if (!raw) return [];

  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return [];
  }

  const map = {
    documentId: "Document ID",
    attachmentId: "Attachment ID",
    remarkId: "Remark ID",
    fallbackDcUserId: "Fallback DC User ID",
    sourceUserId: "Source User ID",
    targetUserId: "Target User ID",
    username: "Username",
    role: "Role",
  };

  return Object.entries(parsed)
    .map(([key, value]) => ({
      key,
      label: map[key] || key.replace(/([A-Z])/g, " $1").replace(/^./, (s) => s.toUpperCase()),
      value: value == null ? "-" : String(value),
    }))
    .sort((a, b) => a.label.localeCompare(b.label));
});

function buildParams() {
  return {
    page: page.value,
    size: size.value,
    fromDate: fromDate.value || undefined,
    toDate: toDate.value || undefined,
    actionType: actionType.value || undefined,
    performedByUserId: performedByUserId.value ? Number(performedByUserId.value) : undefined,
    document: documentFilter.value || undefined,
  };
}

async function load() {
  if (!canViewLogs.value) return;

  loading.value = true;
  error.value = "";

  try {
    const data = await listAuditLogs(buildParams());
    rows.value = data?.content ?? [];
    last.value = !!data?.last;
    totalPages.value = Number(data?.totalPages ?? 0);
  } catch (e) {
    error.value = e?.message || "Failed to load logs.";
    rows.value = [];
    last.value = true;
    totalPages.value = 0;
  } finally {
    loading.value = false;
  }
}

function applyFilters() {
  page.value = 0;
  load();
}

function resetFilters() {
  fromDate.value = "";
  toDate.value = "";
  actionType.value = "";
  documentFilter.value = "";
  performedByUserId.value = "";
  page.value = 0;
  load();
}

function prevPage() {
  if (page.value <= 0) return;
  page.value -= 1;
  load();
}

function nextPage() {
  if (last.value) return;
  page.value += 1;
  load();
}

function changeSize() {
  page.value = 0;
  load();
}

function formatDateTime(value) {
  if (!value) return "-";
  const dt = new Date(value);
  if (Number.isNaN(dt.getTime())) return String(value);
  return dt.toLocaleString();
}

function resolveDocumentId(row) {
  const entityType = String(row?.entityType || "").toUpperCase();
  if ((entityType === "DOCUMENT" || entityType === "MOVEMENT") && row?.entityId) {
    return Number(row.entityId);
  }

  const raw = row?.detailsJson;
  if (!raw) return null;

  try {
    const parsed = JSON.parse(raw);
    const fromJson = Number(parsed?.documentId);
    return Number.isFinite(fromJson) && fromJson > 0 ? fromJson : null;
  } catch {
    const m = String(raw).match(/"documentId"\s*:\s*(\d+)/i);
    return m ? Number(m[1]) : null;
  }
}

async function openView(row) {
  selectedLog.value = row;
  selectedDocument.value = null;
  documentError.value = "";
  showRawJson.value = false;

  const docId = resolveDocumentId(row);
  if (!docId) return;

  documentLoading.value = true;
  try {
    selectedDocument.value = await getDocument(docId);
  } catch (e) {
    documentError.value = e?.message || "Failed to load document details.";
  } finally {
    documentLoading.value = false;
  }
}

function closeView() {
  selectedLog.value = null;
  selectedDocument.value = null;
  documentError.value = "";
  documentLoading.value = false;
  showRawJson.value = false;
}

function downloadCsv() {
  const href = buildAuditLogsExportUrl({
    fromDate: fromDate.value || undefined,
    toDate: toDate.value || undefined,
    actionType: actionType.value || undefined,
    performedByUserId: performedByUserId.value ? Number(performedByUserId.value) : undefined,
    document: documentFilter.value || undefined,
  });

  const a = document.createElement("a");
  a.href = href;
  a.download = "audit-logs.csv";
  a.click();
}

onMounted(() => {
  load();
});
</script>

<style scoped>
.pageHead {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 14px;
}

.pageHead h2 {
  margin: 0;
}

.pageSub {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.card {
  background: #fff;
  padding: 16px;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
}

.filters {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.control {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.control label {
  font-size: 12px;
  font-weight: 700;
  color: #374151;
}

.controlGrow {
  grid-column: span 2;
}

.filterActions {
  display: flex;
  gap: 8px;
  margin: 12px 0;
}

.tableWrap {
  overflow-x: auto;
}

.table {
  width: 100%;
  border-collapse: collapse;
}

.table th,
.table td {
  border-bottom: 1px solid #e5e7eb;
  padding: 10px;
  text-align: left;
  vertical-align: top;
}

.table th {
  color: #374151;
  font-size: 12px;
  font-weight: 800;
}

.small {
  margin-top: 4px;
  color: #6b7280;
  font-size: 12px;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.muted {
  text-align: center;
  color: #6b7280;
}

.pill {
  display: inline-block;
  padding: 4px 8px;
  border-radius: 999px;
  border: 1px solid #dbeafe;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 700;
}

.pager {
  margin-top: 12px;
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: flex-end;
}

.sizeSelect {
  width: 90px;
}

.input {
  height: 38px;
  width: 100%;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 0 10px;
  outline: none;
}

.btn {
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #fff;
  cursor: pointer;
}

.btn:hover {
  background: #f9fafb;
}

.btn-primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.btn-primary:hover {
  background: #1d4ed8;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-sm {
  padding: 6px 8px;
  font-size: 12px;
}

.errorBox {
  margin-bottom: 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #991b1b;
  padding: 10px 12px;
  border-radius: 8px;
}

.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 14px;
  z-index: 2200;
}

.modal {
  width: 100%;
  max-width: 860px;
  max-height: 90vh;
  overflow: auto;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
}

.modalHead {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid #e5e7eb;
}

.modalTitle {
  font-size: 15px;
  font-weight: 800;
}

.modalSub {
  font-size: 12px;
  color: #6b7280;
  margin-top: 2px;
}

.modalBody {
  padding: 14px 16px;
}

.kv {
  display: grid;
  grid-template-columns: 140px 1fr;
  gap: 8px;
  margin-bottom: 8px;
}

.k {
  color: #6b7280;
  font-size: 12px;
  font-weight: 700;
}

.v {
  color: #111827;
  font-size: 13px;
}

.detailsBlock {
  margin-top: 12px;
}

.jsonBox {
  margin: 6px 0 0;
  padding: 10px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #f9fafb;
  color: #111827;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
}

.rawToggle {
  margin-top: 8px;
}

.docGrid {
  margin-top: 8px;
}

.docError {
  color: #991b1b;
}

@media (max-width: 1100px) {
  .filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .controlGrow {
    grid-column: span 1;
  }
}

@media (max-width: 700px) {
  .filters {
    grid-template-columns: 1fr;
  }
}
</style>
