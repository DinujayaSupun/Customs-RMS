<template>
  <AppLayout>
    <div class="pageHead">
      <div class="pageIntro">
        <h2>Audit Logs</h2>
        <p class="pageSub">Review important system activity, narrow the timeline quickly, and inspect each event with full context.</p>
      </div>
      <div class="pageActions">
        <button class="btn" :disabled="loading" @click="refreshAll">Refresh</button>
        <button class="btn btn-primary" :disabled="loading" @click="downloadCsv">Export CSV</button>
      </div>
    </div>

    <div v-if="!canViewLogs" class="errorBox">You do not have permission to access logs.</div>

    <div v-else class="logsStack">
      <div class="summaryGrid">
        <div class="summaryCard">
          <div class="summaryLabel">Matching Logs</div>
          <div class="summaryValue">{{ totalElements }}</div>
          <div class="summaryHint">All results for current filters</div>
        </div>
        <div class="summaryCard">
          <div class="summaryLabel">Current Page</div>
          <div class="summaryValue">{{ rows.length }}</div>
          <div class="summaryHint">{{ currentPageSummary }}</div>
        </div>
        <div class="summaryCard">
          <div class="summaryLabel">Active Filters</div>
          <div class="summaryValue">{{ activeFilterChips.length }}</div>
          <div class="summaryHint">{{ activeFilterChips.length ? "Filters are narrowing results" : "Showing the full activity stream" }}</div>
        </div>
      </div>

      <div class="card filtersCard">
        <div class="sectionHead">
          <div>
            <div class="sectionTitle">Filter Logs</div>
            <div class="sectionSub">Use broad filters first, then drill into a user, action, or document.</div>
          </div>
          <div class="shortcutRow">
            <button class="chipBtn" type="button" :disabled="loading" @click="setQuickRange('today')">Today</button>
            <button class="chipBtn" type="button" :disabled="loading" @click="setQuickRange('7d')">Last 7 Days</button>
            <button class="chipBtn" type="button" :disabled="loading" @click="setQuickRange('30d')">Last 30 Days</button>
            <button class="chipBtn" type="button" :disabled="loading" @click="clearDateRange">Clear Dates</button>
          </div>
        </div>

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
              <option v-for="a in actionOptions" :key="a" :value="a">{{ displayActionTypeLabel(a) }}</option>
            </select>
          </div>

          <div class="control controlGrow">
            <label>Document Ref / ID</label>
            <input v-model="documentFilter" class="input" placeholder="ex: REF-2026-001 or 42" />
          </div>

          <div class="control controlGrow">
            <label>Performed By</label>
            <input
              v-model="performedBySearch"
              class="input"
              list="performedByList"
              placeholder="Search user name or ID"
              @change="syncPerformedBySelection"
              @blur="syncPerformedBySelection"
            />
            <datalist id="performedByList">
              <option
                v-for="u in performedByOptions"
                :key="`performer-${u.id}`"
                :value="performerLabel(u)"
              />
            </datalist>
          </div>
        </div>

        <div class="filterFooter">
          <div class="activeFilters" v-if="activeFilterChips.length">
            <span v-for="chip in activeFilterChips" :key="chip.key" class="activeChip">
              {{ chip.label }}
              <button type="button" class="chipRemove" @click="clearFilter(chip.key)">×</button>
            </span>
          </div>
          <div class="filterActions">
            <button class="btn btn-primary" :disabled="loading" @click="applyFilters">Apply</button>
            <button class="btn" :disabled="loading" @click="resetFilters">Reset</button>
          </div>
        </div>
      </div>

      <div v-if="error" class="errorBox">{{ error }}</div>

      <div class="card tableCard">
        <div class="sectionHead">
          <div>
            <div class="sectionTitle">Log Results</div>
            <div class="sectionSub">Select any row to inspect the full event details and linked document context.</div>
          </div>
        </div>

        <div class="tableWrap">
          <table class="table">
            <colgroup>
              <col class="col-time" />
              <col class="col-action" />
              <col class="col-entity" />
              <col class="col-entity-id" />
              <col class="col-doc-ref" />
              <col class="col-user" />
              <col class="col-message" />
              <col class="col-view" />
            </colgroup>
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
                <td colspan="8" class="muted stateCell">Loading logs...</td>
              </tr>
              <tr v-else-if="rows.length === 0">
                <td colspan="8" class="muted stateCell">No logs found for current filters.</td>
              </tr>
              <tr v-else v-for="row in rows" :key="row.id" class="tableRow" @click="openView(row)">
                <td class="mono">{{ formatDateTime(row.performedAt) }}</td>
                <td><span class="pill">{{ displayActionTypeLabel(row.actionType) || "-" }}</span></td>
                <td :title="row.entityType || '-'"><span class="truncateText">{{ row.entityType || "-" }}</span></td>
                <td>{{ row.entityId ?? "-" }}</td>
                <td :title="row.documentRef || '-'"><span class="truncateText">{{ row.documentRef || "-" }}</span></td>
                <td :title="performerDisplayName(row)">
                  <span class="truncateText">{{ performerDisplayName(row) }}</span>
                </td>
                <td>
                  <div class="truncateText" :title="displayLogMessage(row) || '-'">{{ displayLogMessage(row) || "-" }}</div>
                  <div v-if="row.detailsJson" class="small mono truncateText" :title="row.detailsJson">{{ row.detailsJson }}</div>
                </td>
                <td>
                  <button class="btn btn-sm" @click.stop="openView(row)">View</button>
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
    </div>

    <div v-if="selectedLog" class="overlay">
      <div class="modal">
        <div class="modalHead">
          <div>
            <div class="modalTitle">Log Details</div>
            <div class="modalSub">ID {{ selectedLog.id }} • {{ displayActionTypeLabel(selectedLog.actionType) || "-" }}</div>
          </div>
          <button class="btn btn-sm" @click="closeView">Close</button>
        </div>

        <div class="modalBody">
          <div class="modalSection">
            <div class="sectionTitle">Event Summary</div>
            <div class="modalGrid">
              <div class="kv"><span class="k">Performed At</span><span class="v">{{ formatDateTime(selectedLog.performedAt) }}</span></div>
              <div class="kv"><span class="k">Action</span><span class="v">{{ displayActionTypeLabel(selectedLog.actionType) || "-" }}</span></div>
              <div class="kv"><span class="k">Entity Type</span><span class="v">{{ selectedLog.entityType || "-" }}</span></div>
              <div class="kv"><span class="k">Entity ID</span><span class="v">{{ selectedLog.entityId ?? "-" }}</span></div>
              <div class="kv"><span class="k">Document Ref</span><span class="v">{{ selectedLog.documentRef || "-" }}</span></div>
              <div class="kv"><span class="k">Performed By</span><span class="v">{{ performerDisplayName(selectedLog) }}</span></div>
              <div class="kv kvFull"><span class="k">Message</span><span class="v">{{ displayLogMessage(selectedLog) || "-" }}</span></div>
            </div>
          </div>

          <div class="modalSection">
            <div class="sectionTitle">Additional Details</div>
            <div v-if="readableDetails.length" class="modalGrid">
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

          <div class="modalSection">
            <div class="sectionTitle">Linked Document</div>
            <div v-if="documentLoading" class="small">Loading document details...</div>
            <div v-else-if="documentError" class="small docError">{{ documentError }}</div>
            <div v-else-if="selectedDocument" class="modalGrid">
              <div class="kv"><span class="k">Ref No</span><span class="v">{{ selectedDocument.refNo || "-" }}</span></div>
              <div class="kv"><span class="k">Title</span><span class="v">{{ selectedDocument.title || "-" }}</span></div>
              <div class="kv"><span class="k">Company</span><span class="v">{{ selectedDocument.companyName || "-" }}</span></div>
              <div class="kv"><span class="k">Status</span><span class="v">{{ displayStatusLabel(selectedDocument.status) || "-" }}</span></div>
              <div class="kv"><span class="k">Priority</span><span class="v">{{ selectedDocument.priority || "-" }}</span></div>
              <div class="kv"><span class="k">Received Date</span><span class="v">{{ selectedDocument.receivedDate || "-" }}</span></div>
              <div class="kv"><span class="k">Created By ID</span><span class="v">{{ documentUserLabel(selectedDocument.createdByName, selectedDocument.createdByUserId) }}</span></div>
                  <div class="kv"><span class="k">Report At ID</span><span class="v">{{ documentUserLabel(selectedDocument.currentOwnerName, selectedDocument.currentOwnerUserId) }}</span></div>
              <div class="kv"><span class="k">Created At</span><span class="v">{{ formatDateTime(selectedDocument.createdAt) }}</span></div>
                  <div class="kv"><span class="k">Approved At</span><span class="v">{{ formatDateTime(selectedDocument.completedAt) }}</span></div>
                  <div class="kv"><span class="k">Done At</span><span class="v">{{ formatDateTime(selectedDocument.issuedAt) }}</span></div>
            </div>
            <div v-else class="small">No document linked for this log entry.</div>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from "vue";
import AppLayout from "../layouts/AppLayout.vue";
import { getCurrentUser, hasPermission } from "../auth/currentUser";
import { getDocument } from "../api/documents.api";
import { exportAuditLogsCsv, getAuditLogFilterOptions, listAuditLogs } from "../api/logs.api";
import { formatDateTimeSafe } from "../utils/dateFormat";

const currentUser = ref(getCurrentUser());
const canViewLogs = computed(() => hasPermission(currentUser.value, "VIEW_LOGS"));

function refreshCurrentUser() {
  currentUser.value = getCurrentUser();
}

const loading = ref(false);
const error = ref("");
const rows = ref([]);

const page = ref(0);
const size = ref(20);
const last = ref(true);
const totalPages = ref(0);
const totalElements = ref(0);

const fromDate = ref("");
const toDate = ref("");
const actionType = ref("");
const documentFilter = ref("");
const performedByUserId = ref("");
const performedBySearch = ref("");
const selectedLog = ref(null);
const selectedDocument = ref(null);
const documentLoading = ref(false);
const documentError = ref("");
const showRawJson = ref(false);
const actionOptions = ref([]);
const performedByOptions = ref([]);

const totalPagesDisplay = computed(() => Math.max(totalPages.value, 1));
const currentPageSummary = computed(() => {
  if (totalElements.value === 0) return "No matching results";
  const start = page.value * size.value + 1;
  const end = Math.min((page.value * size.value) + rows.value.length, totalElements.value);
  return `${start}-${end} of ${totalElements.value}`;
});
const activeFilterChips = computed(() => {
  const chips = [];
  if (fromDate.value) chips.push({ key: "fromDate", label: `From ${fromDate.value}` });
  if (toDate.value) chips.push({ key: "toDate", label: `To ${toDate.value}` });
  if (actionType.value) chips.push({ key: "actionType", label: `Action ${displayActionTypeLabel(actionType.value)}` });
  if (documentFilter.value) chips.push({ key: "documentFilter", label: `Document ${documentFilter.value}` });
  if (performedByUserId.value && performedBySearch.value) {
    chips.push({ key: "performedBy", label: `Performed by ${performedBySearch.value}` });
  }
  return chips;
});

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
  remarkId: "Minute ID",
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
    totalElements.value = Number(data?.totalElements ?? 0);
  } catch (e) {
    error.value = e?.message || "Failed to load logs.";
    rows.value = [];
    last.value = true;
    totalPages.value = 0;
    totalElements.value = 0;
  } finally {
    loading.value = false;
  }
}

async function loadFilterOptions() {
  if (!canViewLogs.value) return;

  try {
    const data = await getAuditLogFilterOptions();
    actionOptions.value = Array.isArray(data?.actionTypes) ? data.actionTypes : [];
    performedByOptions.value = Array.isArray(data?.performers) ? data.performers : [];
  } catch {
    actionOptions.value = [];
    performedByOptions.value = [];
  }
}

function performerLabel(option) {
  const name = option?.name || "Unknown user";
  return option?.id == null ? name : `${name} (ID ${option.id})`;
}

function performerDisplayName(row) {
  const name = row?.performedByUserName || "Unknown user";
  return row?.performedByUserId == null ? name : `${name} (ID ${row.performedByUserId})`;
}

function documentUserLabel(name, id) {
  const displayName = name || "Unknown user";
  return id == null ? displayName : `${displayName} (ID ${id})`;
}

function syncPerformedBySelection() {
  const value = String(performedBySearch.value || "").trim();
  if (!value) {
    performedByUserId.value = "";
    return;
  }

  const matched = performedByOptions.value.find((option) => performerLabel(option) === value);
  performedByUserId.value = matched ? String(matched.id) : "";
}

function setQuickRange(preset) {
  const today = new Date();
  const format = (value) => value.toISOString().slice(0, 10);

  if (preset === "today") {
    const current = format(today);
    fromDate.value = current;
    toDate.value = current;
    return;
  }

  const days = preset === "7d" ? 6 : 29;
  const start = new Date(today);
  start.setDate(today.getDate() - days);
  fromDate.value = format(start);
  toDate.value = format(today);
}

function clearDateRange() {
  fromDate.value = "";
  toDate.value = "";
}

function clearFilter(key) {
  if (key === "fromDate") fromDate.value = "";
  if (key === "toDate") toDate.value = "";
  if (key === "actionType") actionType.value = "";
  if (key === "documentFilter") documentFilter.value = "";
  if (key === "performedBy") {
    performedByUserId.value = "";
    performedBySearch.value = "";
  }
}

function refreshAll() {
  loadFilterOptions();
  load();
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
  performedBySearch.value = "";
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

const formatDateTime = formatDateTimeSafe;

function displayStatusLabel(statusValue) {
  return String(statusValue || "").toUpperCase() === "ISSUED" ? "DONE" : statusValue;
}

function displayActionTypeLabel(actionType) {
  const normalized = String(actionType || "").toUpperCase();
  if (normalized === "ISSUE") return "DONE";
  if (normalized === "REMARK") return "MINUTE";
  return actionType;
}

function displayLogMessage(row) {
  const message = String(row?.message || "");
  const actionType = String(row?.actionType || "").toUpperCase();
  if (!message) return "";
  let next = message;
  if (actionType === "ISSUE") {
    next = next.replace(/\bIssued\b/g, "Done").replace(/\bissue\b/g, "done");
  }
  next = next
    .replace(/\bRemarks\b/g, "Minutes")
    .replace(/\bRemark\b/g, "Minute")
    .replace(/\bremarks\b/g, "minutes")
    .replace(/\bremark\b/g, "minute");
  return next;
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

async function downloadCsv() {
  try {
    const { blob, fileName } = await exportAuditLogsCsv({
      fromDate: fromDate.value || undefined,
      toDate: toDate.value || undefined,
      actionType: actionType.value || undefined,
      performedByUserId: performedByUserId.value ? Number(performedByUserId.value) : undefined,
      document: documentFilter.value || undefined,
    });

    const href = window.URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = href;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(href);
  } catch (e) {
    error.value = e?.message || "Failed to export logs.";
  }
}

onMounted(() => {
  window.addEventListener("rms_auth_changed", refreshCurrentUser);
  window.addEventListener("rms_permissions_updated", refreshCurrentUser);
  refreshAll();
});

onUnmounted(() => {
  window.removeEventListener("rms_auth_changed", refreshCurrentUser);
  window.removeEventListener("rms_permissions_updated", refreshCurrentUser);
});
</script>

<style scoped>
.pageHead {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}

.pageHead h2 {
  margin: 0;
  font-size: 30px;
}

.pageIntro {
  max-width: 760px;
}

.pageSub {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.pageActions {
  display: flex;
  gap: 10px;
}

.logsStack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summaryGrid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.summaryCard {
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  border: 1px solid #dbeafe;
  border-radius: 16px;
  padding: 16px 18px;
}

.summaryLabel {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.summaryValue {
  margin-top: 10px;
  font-size: 30px;
  font-weight: 800;
  color: #0f172a;
}

.summaryHint {
  margin-top: 6px;
  font-size: 13px;
  color: #64748b;
}

.card {
  background: #fff;
  padding: 18px;
  border-radius: 16px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.04);
}

.sectionHead {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 14px;
}

.sectionTitle {
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.sectionSub {
  margin-top: 4px;
  font-size: 13px;
  color: #64748b;
}

.shortcutRow {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chipBtn {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid #dbeafe;
  background: #f8fbff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
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
  justify-content: flex-end;
}

.filterFooter {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
}

.activeFilters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.activeChip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  border-radius: 999px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
}

.chipRemove {
  width: 18px;
  height: 18px;
  border: 0;
  border-radius: 999px;
  background: rgba(29, 78, 216, 0.12);
  color: #1d4ed8;
  cursor: pointer;
}

.tableWrap {
  overflow-x: auto;
}

.table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}
.col-time { width: 165px; }
.col-action { width: 120px; }
.col-entity { width: 110px; }
.col-entity-id { width: 90px; }
.col-doc-ref { width: 150px; }
.col-user { width: 180px; }
.col-message { width: 280px; }
.col-view { width: 80px; }
.truncateText {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table th,
.table td {
  border-bottom: 1px solid #e5e7eb;
  padding: 12px 10px;
  text-align: left;
  vertical-align: top;
}

.table th {
  color: #374151;
  font-size: 12px;
  font-weight: 800;
}

.tableRow {
  cursor: pointer;
  transition: background .18s ease;
}

.tableRow:hover {
  background: #f8fbff;
}

.stateCell {
  padding: 24px 10px !important;
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
  flex-wrap: wrap;
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
  background: rgba(15, 23, 42, 0.52);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 14px;
  z-index: 2200;
}

.modal {
  width: 100%;
  max-width: 960px;
  min-width: 0;
  box-sizing: border-box;
  max-height: 90vh;
  overflow: auto;
  background: #fff;
  border-radius: 16px;
  border: 1px solid #e5e7eb;
}

.modalHead {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
  padding: 14px 16px;
  border-bottom: 1px solid #e5e7eb;
}

.modalHead > div:first-child {
  flex: 1 1 auto;
  min-width: 0;
  max-width: 100%;
}

.modalTitle {
  font-size: 15px;
  font-weight: 800;
  max-width: 100%;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.modalSub {
  font-size: 12px;
  color: #6b7280;
  margin-top: 2px;
  max-width: 100%;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.modalBody {
  padding: 18px 18px 20px;
}

.kv {
  display: grid;
  grid-template-columns: minmax(0, 140px) minmax(0, 1fr);
  gap: 8px;
  min-width: 0;
  margin-bottom: 8px;
}

.kvFull {
  grid-column: 1 / -1;
}

.k {
  min-width: 0;
  color: #6b7280;
  font-size: 12px;
  font-weight: 700;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.v {
  min-width: 0;
  color: #111827;
  font-size: 13px;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.detailsBlock {
  margin-top: 12px;
}

.modalSection + .modalSection {
  margin-top: 18px;
}

.modalGrid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 18px;
  margin-top: 10px;
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
  .summaryGrid {
    grid-template-columns: 1fr;
  }

  .filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .controlGrow {
    grid-column: span 1;
  }

  .filterFooter,
  .sectionHead {
    flex-direction: column;
    align-items: stretch;
  }

  .modalGrid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .pageHead {
    flex-direction: column;
    align-items: stretch;
  }

  .pageActions {
    justify-content: flex-start;
  }

  .filters {
    grid-template-columns: 1fr;
  }

  .filterFooter {
    align-items: stretch;
  }
}
</style>
