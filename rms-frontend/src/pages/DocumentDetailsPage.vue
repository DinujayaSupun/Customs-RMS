<template>
  <AppLayout>
    <!-- TOP BAR -->
    <div class="topbar">
      <div>
        <h2 class="title">Document #{{ doc?.refNo || ("ID " + documentId) }}</h2>

        <div class="meta">
          <span class="pill">Status: {{ doc?.status || "-" }}</span>
          <span class="pill">Priority: {{ doc?.priority || "-" }}</span>
          <span class="pill">Owner: {{ ownerLabel }}</span>
        </div>

        <div class="subMeta">
          <span class="smallHint">
            Created by: <b>{{ createdByLabel }}</b>
          </span>
          <span class="dot">•</span>
          <span class="smallHint">
            Received: <b>{{ formatDate(doc?.receivedDate) }}</b>
          </span>
        </div>
      </div>

      <div class="rightBtns">
        <button class="btn btn-primary" @click="openViewer(mainFile)" :disabled="!mainFile">
          Open Viewer
        </button>
        <button class="btn" @click="reloadAll" :disabled="busy">Refresh</button>
        <button class="btn" @click="goBack">Back</button>
      </div>
    </div>

    <!-- ERROR -->
    <div v-if="error" class="errorBox">
      <b>Error:</b> {{ error }}
    </div>

    <div v-if="successMessage" class="successBox">
      <b>Success:</b> {{ successMessage }}
    </div>

    <!-- LOADING OVERLAY -->
    <div v-if="busy" class="busyOverlay">
      <div class="busyCard">
        <div class="spinner"></div>
        <div>
          <div class="busyTitle">Loading...</div>
          <div class="busySub">Please wait</div>
        </div>
      </div>
    </div>

    <!-- MAIN GRID -->
    <div v-if="doc" class="grid">
      <!-- LEFT -->
      <div class="col">
        <!-- DETAILS -->
        <div class="card">
          <div class="cardHead">
            <div class="cardTitle">Details</div>
            <div class="btnRow" style="margin-top:0;">
              <button
                v-if="canEditDetails && !isEditingDetails"
                class="btn"
                :disabled="busy"
                @click="startEditDetails"
              >
                Edit Details
              </button>
              <template v-if="isEditingDetails">
                <button class="btn" :disabled="busy" @click="cancelEditDetails">Cancel</button>
                <button class="btn btn-primary" :disabled="busy" @click="saveDetails">
                  {{ busy ? "Saving..." : "Save Details" }}
                </button>
              </template>
            </div>
          </div>

          <div v-if="!isEditingDetails" class="kv">
            <div class="k">Ref No</div>
            <div class="v">{{ doc.refNo }}</div>

            <div class="k">Title</div>
            <div class="v">{{ doc.title }}</div>

            <div class="k">Company</div>
            <div class="v">{{ doc.companyName }}</div>

            <div class="k">Priority</div>
            <div class="v">{{ doc.priority }}</div>

            <div class="k">Received Date</div>
            <div class="v">{{ formatDate(doc.receivedDate) }}</div>

            <div class="k">Created By</div>
            <div class="v">{{ createdByLabel }}</div>

            <div class="k">Created At</div>
            <div class="v mono">{{ formatDateTime(doc.createdAt) }}</div>

            <div class="k">Completed At</div>
            <div class="v mono">{{ completedAtDisplay }}</div>

            <div class="k">Issued At</div>
            <div class="v mono">{{ issuedAtDisplay }}</div>
          </div>

          <div v-else>
            <div class="formRow">
              <div class="label">Ref No</div>
              <input class="input" v-model="detailsForm.refNo" :disabled="busy" />
            </div>

            <div class="formRow">
              <div class="label">Title</div>
              <input class="input" v-model="detailsForm.title" :disabled="busy" />
            </div>

            <div class="formRow">
              <div class="label">Company</div>
              <input class="input" v-model="detailsForm.companyName" :disabled="busy" />
            </div>

            <div class="formRow">
              <div class="label">Priority</div>
              <select class="input" v-model="detailsForm.priority" :disabled="busy">
                <option value="LOW">LOW</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="HIGH">HIGH</option>
                <option value="URGENT">URGENT</option>
              </select>
            </div>

            <div class="formRow">
              <div class="label">Received Date (Read Only)</div>
              <input class="input" :value="formatDate(doc.receivedDate)" disabled />
            </div>
          </div>
        </div>

        <!-- ACTIONS -->
        <div class="card">
          <div class="cardTitle">Workflow Actions</div>

          <div class="hint">
            Current User:
            <b>{{ formatUserLabel(currentUser) }}</b>
            <span class="dot">•</span>
            Owner:
            <b :style="{ color: isOwner ? '#065f46' : '#991b1b' }">
              {{ isOwner ? "YES" : "NO" }}
            </b>
          </div>

          <!-- ✅ ONE remark box (used for forward + manual save) -->
          <div class="formRow">
            <div class="label">Remark (optional)</div>

            <textarea
              class="textarea"
              v-model="remarkDraft"
              :disabled="busy || !canTypeRemark"
              placeholder="Type remark..."
            ></textarea>

            <div class="smallHint">
              {{ canAddRemark
                ? "This remark will be saved when you Forward/Return/Approve/Reject/Issue/Reopen. You can also Save Remark only."
                : "You can view all remarks, but only the current owner can add/save remarks."
              }}
            </div>

            <div class="btnRow" style="margin-top:8px;">
              <button
                class="btn"
                :disabled="busy || !canAddRemark || !remarkDraft.trim()"
                @click="saveRemarkOnly"
              >
                Save Remark
              </button>
            </div>
          </div>

          <div class="formRow">
            <div class="label">Forward To</div>

            <!-- ✅ fixed dropdown -->
            <select class="input" v-model="toUserId" :disabled="busy || !canForward">
              <option :value="null">-- Select user --</option>
              <option v-for="u in forwardTargets" :key="u.id" :value="Number(u.id)">
                {{ formatUserLabel(u) }}
              </option>
            </select>

            <div class="smallHint">
              Only current owner can forward/return. PMA can forward only to DC. Issued cannot move.
            </div>
          </div>

          <div class="btnRow">
            <button class="btn btn-primary" :disabled="busy || !canForward" @click="doForward">
              Forward
            </button>
            <button class="btn" :disabled="busy || !canReturn" @click="doReturn">
              Return
            </button>

            <div class="spacer"></div>

            <button class="btn" :disabled="busy || !canApprove" @click="doApprove">Approve</button>
            <button class="btn" :disabled="busy || !canReject" @click="doReject">Reject</button>
            <button class="btn" :disabled="busy || !canIssue" @click="doIssue">Issue</button>
            <button class="btn" :disabled="busy || !canReopen" @click="doReopen">Reopen</button>
          </div>

          <div class="rules">
            <b>Rules:</b> Forward/Return only by current owner. Approve/Reject/Issue/Reopen only by DC (and must be owner).
          </div>
        </div>

        <!-- ✅ REMARKS LIST (ALWAYS VISIBLE) -->
        <div class="card">
          <div class="cardTitle">Remarks</div>

          <div v-if="remarks.length === 0" class="empty">No remarks yet.</div>

          <div v-else class="list">
            <!-- ✅ correct backend fields -->
            <div v-for="r in remarks" :key="r.id" class="item">
              <div class="itemTop">
                <span class="who">
                  By <b>{{ formatUserLabelById(r.remarkedByUserId, users) }}</b>
                </span>
                <span class="when mono">{{ formatDateTime(r.remarkedAt) }}</span>
              </div>
              <div class="text">{{ r.remarkText }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- RIGHT -->
      <div class="col">
        <!-- FILES -->
        <div class="card">
          <div class="cardTitle">Files</div>

          <!-- (Keeping your existing file visibility rule) -->
          <div v-if="!canViewHistory" class="lockBox">
            Only the <b>current owner</b> can view file history. DC can view all.
          </div>

          <template v-else>
            <div v-if="!mainFile" class="empty">No main file uploaded yet.</div>

            <template v-else>
              <div class="fileRow">
                <div>
                  <div class="fileName">
                    <b>Main:</b> {{ mainFile.fileName }}
                    <span class="ver">(v{{ mainFile.versionNo }})</span>
                  </div>
                  <div class="smallHint">Main file is the first uploaded file (v1).</div>
                </div>
                <div class="btnRow" style="margin-top:0;">
                  <button class="btn btn-primary" @click="openViewer(mainFile)">Preview</button>
                  <button class="btn" @click="openInNewTab(mainFile)">Open</button>
                </div>
              </div>

              <div class="miniPreview">
                <iframe v-if="isPdf(mainFile.fileName)" :src="previewUrl(mainFile.id)" class="miniFrame"></iframe>
                <img v-else-if="isImage(mainFile.fileName)" :src="previewUrl(mainFile.id)" class="miniImg" />
                <div v-else class="noPreview">Preview not available. Use <b>Open</b>.</div>
              </div>
            </template>

            <div class="attachRow">
              <input
                id="attachmentFileInput"
                ref="fileInputRef"
                class="hiddenFileInput"
                type="file"
                @change="onFilePick"
                :disabled="!canUploadAttachments"
              />
              <button
                class="btn"
                type="button"
                :disabled="!canUploadAttachments"
                @click="openFilePicker"
              >
                Choose File
              </button>
              <span class="filePickLabel">{{ pickedFile ? pickedFile.name : "No file chosen" }}</span>
              <button
                class="btn btn-primary"
                :disabled="busy || !pickedFile || !canUploadAttachments"
                @click="uploadPicked"
              >
                Upload Attachment
              </button>
            </div>

            <div class="smallHint">
              Upload allowed only for current owner. First upload becomes Main file (v1). Next uploads are attachments.
            </div>

            <div v-if="attachmentsSorted.length === 0" class="empty">No files yet.</div>

            <div v-else class="list">
              <div v-for="a in attachmentsSorted" :key="a.id" class="item">
                <div class="itemTop">
                  <span class="who">
                    <b>v{{ a.versionNo }}</b> — {{ a.fileName }}
                    <span v-if="Number(a.versionNo) === 1" class="tag">MAIN</span>
                  </span>
                  <span class="when mono">{{ formatDateTime(a.uploadedAt) }}</span>
                </div>

                <div class="btnRow" style="margin-top:10px;">
                  <button class="btn" @click="openViewer(a)">Preview</button>
                  <button class="btn" @click="openInNewTab(a)">Open</button>
                  <button class="btn danger" :disabled="busy || !canUploadAttachments" @click="removeAttachment(a)">
                    Delete
                  </button>
                </div>
              </div>
            </div>
          </template>
        </div>

        <!-- MOVEMENTS -->
        <div class="card">
          <div class="cardTitle">Movement Timeline</div>

          <div v-if="!canViewHistory" class="lockBox">
            Only the <b>current owner</b> can view movement history. DC can view all.
          </div>

          <template v-else>
            <div v-if="movements.length === 0" class="empty">No movements yet.</div>
            <div v-else class="list">
              <div v-for="m in movements" :key="m.id" class="item">
                <div class="itemTop">
                  <span class="who">
                    <b>{{ m.actionType }}</b>
                    <span v-if="m.fromUserId"> | from {{ formatUserLabelById(m.fromUserId, users) }}</span>
                    <span v-if="m.toUserId"> → to {{ formatUserLabelById(m.toUserId, users) }}</span>
                  </span>
                  <span class="when mono">{{ formatDateTime(m.actionAt) }}</span>
                </div>
                <div class="smallHint">Action by: {{ formatUserLabelById(m.actionByUserId, users) }}</div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- SLIDE-OVER VIEWER -->
    <div v-if="viewerOpen" class="viewerOverlay" @click.self="viewerOpen = false">
      <div class="viewerPanel">
        <div class="viewerHead">
          <div>
            <div class="viewerTitle">Document Viewer</div>
            <div class="viewerSub">{{ selectedFile?.fileName || "No file selected" }}</div>
          </div>
          <div class="viewerBtns">
            <button class="btn" :disabled="!selectedFile" @click="selectedFile && openInNewTab(selectedFile)">Open</button>
            <button class="btn" @click="viewerOpen = false">Close</button>
          </div>
        </div>

        <div class="viewerSplit">
          <div class="viewerList">
            <div class="viewerListTitle">Files</div>

            <input class="search" v-model="viewerSearch" placeholder="Search files..." spellcheck="false" />

            <div v-if="filteredViewerFiles.length === 0" class="empty" style="margin-top:10px;">
              No matching files.
            </div>

            <button
              v-for="f in filteredViewerFiles"
              :key="f.id"
              class="viewerItem"
              :class="{ active: selectedFile?.id === f.id }"
              @click="selectFile(f)"
            >
              <div class="viewerItemTop">
                <span><b>v{{ f.versionNo }}</b> {{ f.fileName }}</span>
                <span v-if="Number(f.versionNo) === 1" class="tagSmall">MAIN</span>
              </div>
              <div class="viewerItemSub">{{ formatDateTime(f.uploadedAt) }}</div>
            </button>
          </div>

          <div class="viewerBody">
            <div v-if="!selectedFile" class="noPreviewBig">Select a file from the list.</div>

            <iframe
              v-else-if="isPdf(selectedFile.fileName)"
              :src="previewUrl(selectedFile.id)"
              class="bigFrame"
              title="PDF Viewer"
            ></iframe>

            <img
              v-else-if="isImage(selectedFile.fileName)"
              :src="previewUrl(selectedFile.id)"
              class="bigImg"
              alt="Image Preview"
            />

            <div v-else class="noPreviewBig">
              This file type cannot be previewed in browser. Click <b>Open</b> to download/open it.
            </div>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppLayout from "../layouts/AppLayout.vue";
import { getCurrentUser } from "../auth/currentUser";
import { formatUserLabel, formatUserLabelById } from "../auth/userLabel";
import { listUsers } from "../api/auth.api";
import {
  getDocument,
  updateDocument,
  listMovements,
  listRemarks,
  addRemark,
  listAttachments,
  uploadAttachment,
  deleteAttachment,
  forwardDocument,
  returnDocument,
  approveDocument,
  rejectDocument,
  issueDocument,
  reopenDocument,
  buildAttachmentUrl,
} from "../api/documents.api";

const route = useRoute();
const router = useRouter();
const documentId = Number(route.params.id);

const currentUser = ref(getCurrentUser());
const users = ref([]);

const doc = ref(null);
const movements = ref([]);
const remarks = ref([]);
const attachments = ref([]);

const error = ref("");
const successMessage = ref("");
const busy = ref(false);

const isEditingDetails = ref(false);
const detailsForm = ref({
  refNo: "",
  title: "",
  companyName: "",
  priority: "MEDIUM",
});

const toUserId = ref(null);

// ✅ ONE remark box
const remarkDraft = ref("");

const pickedFile = ref(null);
const fileInputRef = ref(null);

const viewerOpen = ref(false);
const selectedFile = ref(null);
const viewerSearch = ref("");

const isOwner = computed(() => !!doc.value && Number(doc.value.currentOwnerUserId) === Number(currentUser.value.id));
const isDC = computed(() => currentUser.value.role === "DC");
const isIssued = computed(() => !!doc.value && doc.value.status === "ISSUED");
const isEditLocked = computed(() => !!doc.value && (!!doc.value.completedAt || isIssued.value));
const canEditDetails = computed(() => !!doc.value && isOwner.value && !isEditLocked.value);

// Keep your existing “history” rule for files/movements
const canViewHistory = computed(() => !!doc.value && (isOwner.value || isDC.value));

// Upload: only current owner
const canUploadAttachments = computed(() => !!doc.value && isOwner.value && !isIssued.value);

// Actions
const canForward = computed(() => !!doc.value && !isIssued.value && isOwner.value);
const canReturn  = computed(() => !!doc.value && !isIssued.value && isOwner.value);

const canApprove = computed(() => doc.value && !isIssued.value && isOwner.value && isDC.value && doc.value.status !== "APPROVED");
const canReject  = computed(() => doc.value && !isIssued.value && isOwner.value && isDC.value && doc.value.status !== "REJECTED");
const canIssue   = computed(() => doc.value && isOwner.value && isDC.value && doc.value.status === "APPROVED" && !doc.value.issuedAt);
const canReopen  = computed(() => doc.value && !isIssued.value && isOwner.value && isDC.value && ["APPROVED","REJECTED"].includes(doc.value.status));

const completedAtDisplay = computed(() => {
  if (!doc.value?.completedAt) return "-";

  if (isDateOnlyValue(doc.value.completedAt)) {
    const movementTime = findLatestMovementTime(["APPROVE", "REJECT"]);
    if (movementTime) return formatDateTime(movementTime);
    return formatDate(doc.value.completedAt);
  }

  return formatDateTime(doc.value.completedAt);
});

const issuedAtDisplay = computed(() => {
  if (!doc.value?.issuedAt) return "-";

  if (isDateOnlyValue(doc.value.issuedAt)) {
    const movementTime = findLatestMovementTime(["ISSUE"]);
    if (movementTime) return formatDateTime(movementTime);
    return formatDate(doc.value.issuedAt);
  }

  return formatDateTime(doc.value.issuedAt);
});

// Manual add remark: only current owner
const canAddRemark = computed(() => !!doc.value && isOwner.value && !isIssued.value);

// textarea allowed if owner can act/save
const canTypeRemark = computed(() => canAddRemark.value || canForward.value || canReturn.value || canApprove.value || canReject.value || canIssue.value || canReopen.value);

const forwardTargets = computed(() => {
  const all = users.value.filter((u) => Number(u.id) !== Number(currentUser.value.id));
  if (currentUser.value.role === "PMA") return all.filter((u) => u.role === "DC");
  return all;
});

// ✅ Keep toUserId valid
watch(
  forwardTargets,
  (list) => {
    const valid = new Set(list.map((x) => Number(x.id)));
    const cur = toUserId.value;
    if (cur == null || !valid.has(Number(cur))) {
      toUserId.value = list[0] ? Number(list[0].id) : null;
    } else {
      toUserId.value = Number(cur);
    }
  },
  { immediate: true }
);

const attachmentsSorted = computed(() => [...attachments.value].sort((a, b) => Number(a.versionNo) - Number(b.versionNo)));

const mainFile = computed(() => {
  if (!attachmentsSorted.value.length) return null;
  return attachmentsSorted.value.find(a => Number(a.versionNo) === 1) || attachmentsSorted.value[0];
});

const filteredViewerFiles = computed(() => {
  const q = viewerSearch.value.trim().toLowerCase();
  if (!q) return attachmentsSorted.value;
  return attachmentsSorted.value.filter(f =>
    String(f.fileName || "").toLowerCase().includes(q) ||
    String(f.versionNo || "").includes(q)
  );
});

const createdByLabel = computed(() => {
  if (!doc.value) return "-";
  return formatUserLabelById(doc.value.createdByUserId, users.value);
});

const ownerLabel = computed(() => {
  if (!doc.value) return "-";
  return formatUserLabelById(doc.value.currentOwnerUserId, users.value);
});

function isPdf(name) {
  return (name || "").toLowerCase().endsWith(".pdf");
}
function isImage(name) {
  const n = (name || "").toLowerCase();
  return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif") || n.endsWith(".webp");
}

function formatDate(d) {
  if (!d) return "-";
  try {
    const dt = new Date(d);
    if (isNaN(dt.getTime())) return String(d);
    return dt.toLocaleDateString();
  } catch {
    return String(d);
  }
}

function formatDateTime(d) {
  if (!d) return "-";
  try {
    if (isDateOnlyValue(d)) return formatDate(d);
    const dt = new Date(d);
    if (isNaN(dt.getTime())) return String(d);
    return dt.toLocaleString();
  } catch {
    return String(d);
  }
}

function isDateOnlyValue(value) {
  return typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value.trim());
}

function findLatestMovementTime(actionTypes) {
  const wanted = new Set(actionTypes);
  const matches = movements.value
    .filter((m) => wanted.has(String(m.actionType || "").toUpperCase()))
    .map((m) => m.actionAt)
    .filter(Boolean)
    .sort((a, b) => new Date(b).getTime() - new Date(a).getTime());

  return matches[0] || null;
}

function previewUrl(attachmentId) {
  return buildAttachmentUrl(attachmentId, { inline: true });
}
function downloadUrl(attachmentId) {
  return buildAttachmentUrl(attachmentId);
}
function openInNewTab(a) {
  window.open(downloadUrl(a.id), "_blank");
}

function selectFile(f) { selectedFile.value = f; }
function openViewer(file) {
  const target = file || mainFile.value;
  if (!target) return;
  selectedFile.value = target;
  viewerOpen.value = true;
}

function remarkOrNull() {
  const t = remarkDraft.value.trim();
  return t ? t : null;
}

async function reloadAll() {
  error.value = "";
  busy.value = true;
  try {
    doc.value = await getDocument(documentId);

    // ✅ ALWAYS load remarks (so after forward you still see your remark)
    remarks.value = await listRemarks(documentId);

    // Keep your history lock only for movements/files
    if (canViewHistory.value) {
      movements.value = await listMovements(documentId);
      attachments.value = await listAttachments(documentId);
    } else {
      movements.value = [];
      attachments.value = [];
    }

    if (viewerOpen.value) {
      const still = attachmentsSorted.value.find(x => x.id === selectedFile.value?.id);
      selectedFile.value = still || mainFile.value;
    }
  } catch (e) {
    error.value = e?.message || "Failed to load document.";
  } finally {
    busy.value = false;
  }
}

onMounted(async () => {
  try {
    users.value = await listUsers();
  } catch {
    users.value = [];
  }
  await reloadAll();
});

function goBack() {
  router.push("/documents");
}

function startEditDetails() {
  if (!doc.value) return;
  successMessage.value = "";
  error.value = "";
  detailsForm.value = {
    refNo: doc.value.refNo || "",
    title: doc.value.title || "",
    companyName: doc.value.companyName || "",
    priority: doc.value.priority || "MEDIUM",
  };
  isEditingDetails.value = true;
}

function cancelEditDetails() {
  isEditingDetails.value = false;
  error.value = "";
}

async function saveDetails() {
  error.value = "";
  successMessage.value = "";

  if (!canEditDetails.value) {
    error.value = "You are not allowed to edit these details.";
    return;
  }

  const payload = {
    refNo: String(detailsForm.value.refNo || "").trim(),
    title: String(detailsForm.value.title || "").trim(),
    companyName: String(detailsForm.value.companyName || "").trim(),
    priority: detailsForm.value.priority,
  };

  if (!payload.refNo) return (error.value = "Ref No is required.");
  if (!payload.title) return (error.value = "Title is required.");
  if (!payload.companyName) return (error.value = "Company is required.");
  if (!payload.priority) return (error.value = "Priority is required.");

  busy.value = true;
  try {
    await updateDocument(documentId, payload);
    isEditingDetails.value = false;
    await reloadAll();
    successMessage.value = "Document details updated successfully.";
  } catch (e) {
    error.value = e?.message || "Failed to update details.";
  } finally {
    busy.value = false;
  }
}

// ✅ manual save remark only
async function saveRemarkOnly() {
  error.value = "";
  const text = remarkDraft.value.trim();
  if (!text) return;

  busy.value = true;
  try {
    await addRemark(documentId, {
      remarkText: text,
    });
    remarkDraft.value = "";
    await reloadAll();
  } catch (e) {
    error.value = e?.message || "Save remark failed.";
  } finally {
    busy.value = false;
  }
}

async function doForward() {
  error.value = "";
  if (!toUserId.value) return (error.value = "Please select a user to forward.");

  busy.value = true;
  try {
    await forwardDocument(documentId, {
      toUserId: Number(toUserId.value),
      remarkText: remarkOrNull(), // ✅ this is what backend expects
    });
    remarkDraft.value = "";
    await reloadAll();
  } catch (e) {
    error.value = e?.message || "Forward failed.";
  } finally {
    busy.value = false;
  }
}

async function doReturn() {
  error.value = "";
  busy.value = true;
  try {
    await returnDocument(documentId, {
      toUserId: Number(toUserId.value),
      remarkText: remarkOrNull(),
    });
    remarkDraft.value = "";
    await reloadAll();
  } catch (e) {
    error.value = e?.message || "Return failed.";
  } finally {
    busy.value = false;
  }
}

async function doApprove() {
  error.value = "";
  busy.value = true;
  try {
    await approveDocument(documentId, { remarkText: remarkOrNull() });
    remarkDraft.value = "";
    await reloadAll();
  } catch (e) {
    error.value = e?.message || "Approve failed.";
  } finally {
    busy.value = false;
  }
}

async function doReject() {
  error.value = "";
  busy.value = true;
  try {
    await rejectDocument(documentId, { remarkText: remarkOrNull() });
    remarkDraft.value = "";
    await reloadAll();
  } catch (e) {
    error.value = e?.message || "Reject failed.";
  } finally {
    busy.value = false;
  }
}

async function doIssue() {
  error.value = "";
  busy.value = true;
  try {
    await issueDocument(documentId, { remarkText: remarkOrNull() });
    remarkDraft.value = "";
    await reloadAll();
  } catch (e) {
    error.value = e?.message || "Issue failed.";
  } finally {
    busy.value = false;
  }
}

async function doReopen() {
  error.value = "";
  const txt = remarkDraft.value.trim();
  if (!txt) return (error.value = "Reopen requires a reason. Type it in the Remark box first.");

  busy.value = true;
  try {
    await reopenDocument(documentId, { remarkText: txt });
    remarkDraft.value = "";
    await reloadAll();
  } catch (e) {
    error.value = e?.message || "Reopen failed.";
  } finally {
    busy.value = false;
  }
}

function onFilePick(e) {
  pickedFile.value = e.target.files?.[0] ?? null;
}

function openFilePicker() {
  if (!canUploadAttachments.value) return;
  fileInputRef.value?.click();
}

async function uploadPicked() {
  error.value = "";
  if (!pickedFile.value) return;

  busy.value = true;
  try {
    await uploadAttachment(documentId, pickedFile.value);
    pickedFile.value = null;
    if (fileInputRef.value) fileInputRef.value.value = "";
    await reloadAll();
  } catch (e) {
    error.value = e?.message || "Upload failed.";
  } finally {
    busy.value = false;
  }
}
async function removeAttachment(a) {
  error.value = "";
  const ok = window.confirm(`Delete this file?\n\nv${a.versionNo} - ${a.fileName}`);
  if (!ok) return;

  busy.value = true;
  try {
    await deleteAttachment(a.id);
    await reloadAll();
  } catch (e) {
    error.value = e?.message || "Delete failed.";
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
/* Base */
.topbar { display:flex; align-items:flex-start; justify-content:space-between; gap:14px; margin-bottom:12px; }
.title { margin:0; font-size:22px; font-weight:800; }
.meta { display:flex; flex-wrap:wrap; gap:8px; margin-top:8px; }
.pill { font-size:12px; padding:6px 10px; border-radius:999px; background:#eef2ff; border:1px solid #e5e7eb; }
.rightBtns { display:flex; gap:10px; flex-wrap:wrap; }

.subMeta { display:flex; align-items:center; gap:10px; margin-top:8px; }
.dot { color:#9ca3af; }

.grid { display:grid; grid-template-columns: 1.15fr 0.85fr; gap:14px; }
.col { display:flex; flex-direction:column; gap:14px; }

.card { background:#fff; border:1px solid #e5e7eb; border-radius:10px; padding:14px; }
.cardTitle { font-weight:800; margin-bottom:10px; }
.cardHead { display:flex; justify-content:space-between; align-items:center; gap:10px; }

.kv { display:grid; grid-template-columns: 150px 1fr; gap:8px 12px; }
.k { font-size:12px; color:#6b7280; font-weight:700; }
.v { font-size:14px; color:#111827; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size:12px; }

.formRow { margin-top:10px; }
.label { font-size:12px; font-weight:800; margin-bottom:6px; color:#374151; }
.input { height:38px; width:100%; border:1px solid #e5e7eb; border-radius:8px; padding:0 10px; outline:none; }
.textarea { width:100%; min-height:80px; border:1px solid #e5e7eb; border-radius:8px; padding:10px; outline:none; resize:vertical; }
.smallHint { margin-top:6px; font-size:12px; color:#6b7280; }

.btnRow { display:flex; gap:10px; align-items:center; margin-top:10px; flex-wrap:wrap; }
.spacer { flex:1; }

.btn { padding:10px 12px; border-radius:8px; border:1px solid #e5e7eb; background:#fff; cursor:pointer; }
.btn:hover { background:#f9fafb; }
.btn-primary { background:#2563eb; border-color:#2563eb; color:#fff; }
.btn-primary:hover { background:#1d4ed8; }
.btn:disabled { opacity:0.6; cursor:not-allowed; }
.danger { border-color:#fecaca; background:#fff; color:#991b1b; }
.danger:hover { background:#fef2f2; }

.rules { margin-top:10px; font-size:12px; color:#6b7280; }

.errorBox { background:#fef2f2; border:1px solid #fecaca; color:#991b1b; padding:10px 12px; border-radius:8px; margin-bottom:12px; }
.successBox { background:#ecfdf5; border:1px solid #a7f3d0; color:#065f46; padding:10px 12px; border-radius:8px; margin-bottom:12px; }
.lockBox { background:#fff7ed; border:1px solid #fed7aa; color:#9a3412; padding:10px 12px; border-radius:8px; }

.empty { font-size:13px; color:#6b7280; padding:8px 0; }

.list { display:flex; flex-direction:column; gap:10px; margin-top:10px; }
.item { border:1px solid #e5e7eb; border-radius:10px; padding:10px; background:#fafafa; }
.itemTop { display:flex; justify-content:space-between; gap:10px; }
.who { font-size:13px; color:#111827; }
.when { color:#6b7280; font-size:12px; }
.text { margin-top:8px; font-size:13px; color:#111827; white-space:pre-wrap; }

.attachRow { display:flex; gap:10px; align-items:center; flex-wrap:wrap; margin-top:12px; }
.hiddenFileInput { display:none; }
.filePickLabel {
  font-size:13px;
  color:#374151;
  max-width:280px;
  white-space:nowrap;
  overflow:hidden;
  text-overflow:ellipsis;
}

.fileRow { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; }
.fileName { font-size:14px; }
.ver { color:#6b7280; font-size:12px; margin-left:6px; }
.miniPreview { margin-top:10px; }
.miniFrame { width:100%; height:220px; border:1px solid #e5e7eb; border-radius:8px; background:#fff; }
.miniImg { max-width:100%; max-height:220px; border:1px solid #e5e7eb; border-radius:8px; background:#fff; display:block; }
.noPreview { font-size:13px; color:#6b7280; padding:10px; background:#fff; border:1px solid #e5e7eb; border-radius:8px; }

.tag { margin-left:8px; font-size:11px; padding:2px 8px; border-radius:999px; background:#dbeafe; color:#1e40af; border:1px solid #bfdbfe; }

/* Busy overlay */
.busyOverlay {
  position: fixed;
  inset: 0;
  background: rgba(255,255,255,0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 60;
}
.busyCard {
  display:flex;
  gap:12px;
  align-items:center;
  background:#fff;
  border:1px solid #e5e7eb;
  border-radius:12px;
  padding:14px 16px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.06);
}
.spinner {
  width: 26px;
  height: 26px;
  border: 3px solid #e5e7eb;
  border-top-color: #2563eb;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
.busyTitle { font-weight:900; color:#111827; }
.busySub { font-size:12px; color:#6b7280; margin-top:2px; }
@keyframes spin { to { transform: rotate(360deg); } }

/* Viewer */
.viewerOverlay {
  position:fixed; inset:0;
  background:rgba(0,0,0,0.35);
  display:flex;
  justify-content:flex-end;
  z-index:50;
}
.viewerPanel {
  width:min(980px, 96vw);
  height:100%;
  background:#fff;
  display:flex;
  flex-direction:column;
  border-left:1px solid #e5e7eb;
}
.viewerHead {
  padding:14px 16px;
  border-bottom:1px solid #e5e7eb;
  display:flex;
  justify-content:space-between;
  gap:10px;
}
.viewerTitle { font-weight:900; }
.viewerSub { font-size:12px; color:#6b7280; margin-top:2px; }
.viewerBtns { display:flex; gap:10px; align-items:center; }

.viewerSplit { flex:1; display:grid; grid-template-columns: 320px 1fr; min-height:0; }
.viewerList { border-right:1px solid #e5e7eb; padding:12px; overflow:auto; background:#fafafa; }
.viewerListTitle { font-weight:900; margin-bottom:10px; color:#111827; }

.search {
  width: 100%;
  height: 38px;
  border:1px solid #e5e7eb;
  border-radius:10px;
  padding:0 10px;
  outline:none;
  background:#fff;
}

.viewerItem {
  width:100%;
  text-align:left;
  border:1px solid #e5e7eb;
  background:#fff;
  border-radius:10px;
  padding:10px;
  margin-top:10px;
  cursor:pointer;
}
.viewerItem:hover { background:#f9fafb; }
.viewerItem.active { border-color:#2563eb; box-shadow:0 0 0 2px rgba(37,99,235,0.12); }

.viewerItemTop { display:flex; justify-content:space-between; gap:10px; font-size:12px; color:#111827; }
.viewerItemSub { font-size:11px; color:#6b7280; margin-top:6px; }
.tagSmall { font-size:10px; padding:2px 8px; border-radius:999px; background:#dbeafe; color:#1e40af; border:1px solid #bfdbfe; }

.viewerBody { padding:12px; overflow:auto; min-height:0; }
.bigFrame { width:100%; height:100%; min-height:75vh; border:1px solid #e5e7eb; border-radius:10px; background:#fff; }
.bigImg { max-width:100%; max-height:82vh; display:block; margin:0 auto; border:1px solid #e5e7eb; border-radius:10px; background:#fff; }
.noPreviewBig { padding:14px; border:1px solid #e5e7eb; border-radius:10px; background:#fafafa; color:#6b7280; }
</style>