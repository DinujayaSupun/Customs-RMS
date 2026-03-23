<template>
  <AppLayout>
    <div class="inboxCanvas">
      <div class="pageHead">
        <div class="titleBlock">
          <h2>My Inbox</h2>
          <p class="pageSub">Scan assigned documents in a message-style view and open items faster.</p>
        </div>
        <div class="headActions">
          <button class="btn" @click="load">Refresh</button>
        </div>
      </div>

      <div class="card filtersCard">
        <div class="filters">
          <div class="control controlSearch">
            <label class="controlLabel">Search</label>
            <input v-model="q" class="input" placeholder="Search ref/title/company..." />
          </div>

          <div class="control">
            <label class="controlLabel">Status</label>
            <select v-model="status" class="input">
              <option value="">All Status</option>
              <option>PENDING</option>
              <option>IN_PROGRESS</option>
              <option>RETURNED</option>
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

        <div class="chipsRow">
          <button class="chip" :class="{ chipActive: viewFilter === 'all' }" @click="viewFilter = 'all'">All</button>
          <button class="chip" :class="{ chipActive: viewFilter === 'unopened' }" @click="viewFilter = 'unopened'">Unopened</button>
          <button class="chip" :class="{ chipActive: viewFilter === 'opened' }" @click="viewFilter = 'opened'">Opened</button>
          <button class="chip" :class="{ chipActive: viewFilter === 'urgent' }" @click="viewFilter = 'urgent'">Urgent</button>
        </div>
      </div>

      <div v-if="error" class="errorBox">{{ error }}</div>

      <div class="card inboxCard">
        <div class="inboxHead">
          <div class="inboxTitleWrap">
            <span class="inboxTitle">Inbox Documents</span>
            <span class="inboxMeta">{{ rows.length }} item{{ rows.length === 1 ? '' : 's' }}</span>
          </div>
          <div class="tableHintWrap">
            <span class="tableHintLabel">Inbox Info</span>
            <HoverHint :text="`${sortHint}. Unopened means the document has not been opened by you in the current assignment.`" />
          </div>
        </div>

        <div v-if="loading" class="emptyState">Loading...</div>
        <div v-else-if="rows.length===0" class="emptyState">No inbox items.</div>

        <div v-else class="mailList">
          <article
            v-for="d in rows"
            :key="d.id"
            class="mailRow"
            :class="{ unopened: !isViewedByMe(d) }"
            @click="open(d.id)"
          >
            <div class="mailLeft">
              <span class="unreadDot" :class="{ visible: !isViewedByMe(d) }" aria-hidden="true"></span>
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
            </div>

            <div class="mailCenter">
              <div class="mailTopLine">
                <span class="refNo">{{ d.refNo }}</span>
                <span class="titleText">{{ d.title }}</span>
              </div>
              <div class="mailPreview">
                <template v-if="d.latestRemarkPreview">{{ d.latestRemarkPreview }}</template>
                <template v-else>{{ d.companyName || 'No company' }} • {{ d.status }} • Priority {{ d.priority }}</template>
              </div>
            </div>

            <div class="mailRight">
              <span class="pill" :class="'pill-'+d.status">{{ d.status }}</span>
              <span class="timeText">{{ displayDate(d) }}</span>
              <button class="btn btn-sm" @click.stop="open(d.id)">Open</button>
            </div>
          </article>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from "vue";
import { useRouter } from "vue-router";
import { File, FileText, FileSpreadsheet, Image, Archive } from "lucide-vue-next";
import AppLayout from "../layouts/AppLayout.vue";
import HoverHint from "../components/HoverHint.vue";
import { listDocuments } from "../api/documents.api";
import { getCurrentUser } from "../auth/currentUser";

const router = useRouter();

const loading = ref(false);
const error = ref("");
const allRows = ref([]);
const rows = ref([]);
const q = ref("");
const status = ref("");
const priority = ref("");
const sortBy = ref("recent");
const viewFilter = ref("all");

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

const PRIORITY_ORDER = { LOW: 1, MEDIUM: 2, HIGH: 3, URGENT: 4 };
const STATUS_ORDER = { PENDING: 1, IN_PROGRESS: 2, APPROVED: 3, ISSUED: 4, REJECTED: 5 };

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
    const matchView = (() => {
      if (viewFilter.value === "unopened") return !isViewedByMe(d);
      if (viewFilter.value === "opened") return isViewedByMe(d);
      if (viewFilter.value === "urgent") return String(d.priority || "").toUpperCase() === "URGENT";
      return true;
    })();

    return matchQ && matchStatus && matchPriority && matchView;
  });
}

function applyNow() {
  rows.value = sortDocuments(applyFilters(allRows.value));
}

watch([q, status, priority, sortBy, viewFilter], () => {
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

function isViewedByMe(doc) {
  return !!doc?.viewedByMe;
}

function displayDate(doc) {
  const source = doc?.createdAt || doc?.receivedDate;
  if (!source) return "";
  const parsed = new Date(source);
  if (Number.isNaN(parsed.getTime())) return "";

  const now = new Date();
  const sameDay = parsed.toDateString() === now.toDateString();
  if (sameDay) {
    return parsed.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  }
  return parsed.toLocaleDateString();
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
.inboxCanvas {
  background:
    radial-gradient(90% 70% at 0% 0%, rgba(37, 99, 235, 0.08) 0%, rgba(37, 99, 235, 0) 70%),
    radial-gradient(70% 60% at 100% 0%, rgba(15, 118, 110, 0.08) 0%, rgba(15, 118, 110, 0) 70%);
  border-radius: 14px;
  padding: 10px;
}

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

.filtersCard { margin-bottom:14px; }
.filters {
  display:grid;
  grid-template-columns: 1.8fr 1fr 1fr 1fr;
  gap:12px;
  align-items:end;
}

.controlSearch {
  min-width: 260px;
}

.chipsRow {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.chip {
  border: 1px solid #dbe3ef;
  background: #f8fafc;
  color: #334155;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.chipActive {
  background: #dbeafe;
  color: #1e3a8a;
  border-color: #bfdbfe;
}
.control { display:flex; flex-direction:column; gap:6px; }
.controlLabel { font-size:12px; font-weight:700; color:#374151; }

.card {
  background:#fff;
  padding:16px;
  border-radius:14px;
  border:1px solid #e5e7eb;
  box-shadow:0 6px 18px rgba(17, 24, 39, 0.05);
}

.inboxCard {
  padding:0;
  overflow:hidden;
  position:relative;
  z-index:0;
  isolation:isolate;
}

.inboxHead {
  padding:14px 16px;
  border-bottom:1px solid #e5e7eb;
  display:flex;
  justify-content:space-between;
  align-items:center;
  background:linear-gradient(180deg, #ffffff 0%, #f9fafb 100%);
}
.inboxTitleWrap { display:flex; align-items:baseline; gap:10px; }
.inboxTitle { font-size:14px; font-weight:800; color:#111827; }
.inboxMeta { font-size:12px; color:#6b7280; }
.tableHintWrap { display:flex; align-items:center; gap:8px; }
.tableHintLabel { font-size:12px; color:#6b7280; }

.mailList {
  display: flex;
  flex-direction: column;
}

.mailRow {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid #edf2f7;
  cursor: pointer;
  background: #ffffff;
  transition: background-color 0.18s ease, transform 0.12s ease;
}

.mailRow:hover {
  background: #f8fafc;
}

.mailRow.unopened {
  background: #f5f9ff;
}

.mailLeft {
  display: flex;
  align-items: center;
  gap: 8px;
}

.unreadDot {
  width: 9px;
  height: 9px;
  border-radius: 999px;
  background: transparent;
}

.unreadDot.visible {
  background: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.18);
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

.mailCenter {
  min-width: 0;
}

.mailTopLine {
  display: flex;
  align-items: baseline;
  gap: 9px;
  white-space: nowrap;
  overflow: hidden;
}

.refNo {
  font-size: 13px;
  font-weight: 800;
  color: #0f172a;
  flex-shrink: 0;
}

.titleText {
  font-size: 14px;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mailRow.unopened .titleText,
.mailRow.unopened .refNo {
  font-weight: 800;
}

.mailPreview {
  margin-top: 3px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.mailRight {
  display: flex;
  align-items: center;
  gap: 8px;
}

.timeText {
  font-size: 12px;
  color: #64748b;
  min-width: 62px;
  text-align: right;
}

.emptyState {
  color:#64748b;
  padding:20px;
  text-align:center;
}

.btn { padding:10px 12px; border-radius:10px; border:1px solid #e5e7eb; background:#fff; cursor:pointer; }
.btn:hover { background:#f9fafb; }
.btn-sm { padding:8px 12px; font-size:12px; font-weight:700; }

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
  .filters {
    grid-template-columns:1fr 1fr;
  }

  .inboxHead {
    flex-direction:column;
    align-items:flex-start;
    gap:4px;
  }

  .mailRow {
    grid-template-columns: auto 1fr;
    grid-template-areas:
      "left center"
      "right right";
    row-gap: 8px;
  }

  .mailLeft { grid-area: left; }
  .mailCenter { grid-area: center; }
  .mailRight {
    grid-area: right;
    justify-content: space-between;
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

  .filters {
    grid-template-columns:1fr;
  }

  .btn,
  .btn-sm {
    min-height:36px;
  }
}
</style>