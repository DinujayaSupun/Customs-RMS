<template>
  <AppLayout>
    <!-- TOP BAR -->
    <div class="topbar">
      <div>
        <h2 class="title">Document #{{ doc?.refNo || ("ID " + documentId) }}</h2>

        <div class="meta">
          <span class="pill">Status: {{ displayStatusLabel(doc?.status) || "-" }}</span>
          <span class="pill">Priority: {{ doc?.priority || "-" }}</span>
        <span class="pill">Report At: {{ ownerLabel }}</span>
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
        <div class="card detailsCard">
          <div class="cardHead">
            <div>
              <div class="cardTitle">Details</div>
              <div class="cardSub">Core document metadata and lifecycle dates.</div>
            </div>
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

            <div class="k">Main Attachment Type</div>
            <div class="v">
              <span class="docTypeBadge" :class="'docType-' + docTypeClass(mainAttachmentType)">
                <component :is="attachmentIconComponent(mainAttachmentType)" class="docIcon" aria-hidden="true" />
              </span>
              <span class="typeLabel">{{ mainAttachmentType }}</span>
            </div>

            <div class="k">Received Date</div>
            <div class="v">{{ formatDate(doc.receivedDate) }}</div>

            <div class="k">Days Open</div>
            <div class="v">{{ daysOpenDisplay }}</div>

            <div class="k">Created By</div>
            <div class="v">{{ createdByLabel }}</div>

            <div class="k">Created At</div>
            <div class="v mono">{{ formatDateTime(doc.createdAt) }}</div>

            <div class="k">Approved At</div>
            <div class="v mono">{{ completedAtDisplay }}</div>

              <div class="k">Done At</div>
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
              <div class="label">Received Date</div>
              <input class="input" type="date" v-model="detailsForm.receivedDate" :disabled="busy" />
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

          </div>
        </div>

        <!-- ACTIONS -->
        <div class="card workflowCard">
          <div class="cardTitle">Workflow Actions</div>
          <div class="cardSub">Add a minute, choose the next officer, and run the allowed action.</div>

          <div class="ownershipBanner" :class="{ owner: isOwner }">
            <div>
              <span class="ownershipLabel">Current User</span>
              <b>{{ formatUserLabel(currentUser) }}</b>
            </div>
            <div>
              <span class="ownershipLabel">Report At Status</span>
              <b>
              {{ isOwner ? "You can act on this document" : "Read-only until the document is routed to you in Report At" }}
              </b>
            </div>
          </div>

          <!-- ✅ ONE remark box (used for forward + manual save) -->
          <div class="formRow">
            <div class="label">Minute (optional)</div>

            <textarea
              class="textarea"
              v-model="remarkDraft"
              :disabled="busy || !canTypeRemark"
              placeholder="Type minute..."
            ></textarea>

            <div class="hintInline">
              <span class="hintLabel">Minute help</span>
              <HoverHint :text="canAddRemark
                ? `Your minute will be attached when you run a workflow action (${availableWorkflowActionNames}). You can also save it separately using Save Minute.`
                : 'You can read minutes, but only the user currently shown in Report At with Add Minute permission can add or save minutes.'" />
            </div>

            <div class="btnRow" style="margin-top:8px;">
              <button
                class="btn"
                :disabled="busy || !canAddRemark || !remarkDraft.trim()"
                @click="saveRemarkOnly"
              >
                Save Minute
              </button>
            </div>
          </div>

          <div class="formRow">
            <div class="label">Forward To</div>

            <div class="forwardSearchWrap">
              <input
                class="input forwardSearch"
                v-model="forwardUserSearch"
                :disabled="busy || !canChooseWorkflowTarget"
                placeholder="Search user by name, role, department, or ID..."
                spellcheck="false"
                @focus="forwardSearchFocused = true"
                @blur="forwardSearchFocused = false"
                @keydown.escape="forwardSearchFocused = false"
              />

              <div v-if="showForwardSearchDropdown" class="forwardSearchDropdown">
                <button
                  v-for="u in filteredForwardTargets"
                  :key="u.id"
                  type="button"
                  class="forwardSearchOption"
                  :class="{ active: Number(u.id) === Number(toUserId) }"
                  @mousedown.prevent="selectForwardUser(u)"
                >
                  <span class="forwardUserName">{{ u.fullName || u.name || u.username || `ID ${u.id}` }}</span>
                  <span class="forwardUserMeta">{{ u.username || "-" }} • {{ u.role || "-" }} • ID {{ u.id }}</span>
                </button>
                <div v-if="filteredForwardTargets.length === 0" class="forwardSearchEmpty">
                  No matching users
                </div>
              </div>
            </div>

            <div v-if="selectedForwardUser" class="forwardSelected">
              <span>Selected user</span>
              <b>{{ formatUserLabel(selectedForwardUser) }}</b>
            </div>
            <div v-else class="forwardSelected muted">
              Select a user from the search results before forwarding or returning.
            </div>

            <div class="forwardSearchMeta">
              <span>{{ forwardUserSearch.trim() ? `${filteredForwardTargets.length} of ${forwardTargets.length} users shown` : `${forwardTargets.length} users available` }}</span>
              <button
                v-if="forwardUserSearch"
                type="button"
                class="linkBtn"
                :disabled="busy"
                @click="forwardUserSearch = ''"
              >
                Clear search
              </button>
            </div>

              <div class="hintInline">
                <span class="hintLabel">Forward rules</span>
                <HoverHint :text="`Forward/Return are available only to the user currently shown in Report At with the relevant permission. Return defaults to the most recent sender when available, and you can still change it manually. Allowed statuses are managed from Permissions: ${forwardReturnAllowedStatusesLabel}.`" />
              </div>
          </div>

          <div class="formRow">
            <div class="label">Forward Visibility</div>

            <select class="input" v-model="forwardVisibility" :disabled="busy || !canForward">
              <option v-for="opt in availableForwardVisibilities" :key="opt" :value="opt">
                {{ opt.charAt(0) + opt.slice(1).toLowerCase() }}
              </option>
            </select>

            <div class="hintInline">
              <span class="hintLabel">Visibility help</span>
              <HoverHint :text="`Select the next visibility for this document. Options shown here come from your FORWARD_PUBLIC/FORWARD_PRIVATE permissions. If visibility changes, CHANGE_DOCUMENT_VISIBILITY permission is also required. Available now: ${availableForwardVisibilities.join(', ') || 'None'}.`" />
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

            <button v-if="approveRejectButtonsEnabled" class="btn" :disabled="busy || !canApprove" @click="doApprove">Approve</button>
            <button v-if="approveRejectButtonsEnabled" class="btn" :disabled="busy || !canReject" @click="doReject">Reject</button>
            <button class="btn" :disabled="busy || !canIssue" @click="doIssue">Done</button>
            <button class="btn" :disabled="busy || !canReopen" @click="doReopen">Reopen</button>
          </div>

          <div class="hintInline">
            <span class="hintLabel">Workflow rules</span>
            <HoverHint :text="workflowRulesHint" />
          </div>
        </div>

        <!-- ✅ REMARKS LIST (ALWAYS VISIBLE) -->
          <div class="card minutesCard">
            <div class="cardTitle">Minutes</div>
            <div class="cardSub">Saved notes and action minutes for this document.</div>

          <div v-if="!canViewRemarks" class="warnBox">Minutes are available only when this document is assigned to you in Report At.</div>

          <div v-else-if="remarks.length === 0" class="empty">No minutes yet.</div>

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
        <div class="card filesCard">
          <div class="cardTitle">Files</div>
          <div class="cardSub">Preview, open, upload, or remove document attachments.</div>

          <!-- (Keeping your existing file visibility rule) -->
          <div v-if="!canViewHistory" class="lockBox">
              Only the <b>user currently shown in Report At</b> can view file history.
          </div>

          <template v-else>
            <div v-if="!mainFile" class="empty">No main file uploaded yet.</div>

            <template v-else>
              <div class="fileRow">
                <div>
                  <div class="fileName">
                    <b>Main:</b>
                    <span class="docTypeBadge" :class="'docType-' + docTypeClass(mainAttachmentType)">
                      <component :is="attachmentIconComponent(mainAttachmentType)" class="docIcon" aria-hidden="true" />
                    </span>
                    {{ mainFile.fileName }}
                    <span class="ver">(v{{ mainFile.versionNo }})</span>
                  </div>
                  <div class="hintInline">
                    <span class="hintLabel">Main file</span>
                    <HoverHint text="Main file is version 1 (first uploaded attachment)." />
                  </div>
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

            <div class="hintInline">
              <span class="hintLabel">Upload rules</span>
            <HoverHint text="Upload is allowed only for the user currently shown in Report At with Upload Attachment permission, and is blocked after ISSUED. First upload becomes main file (v1); later uploads are additional versions/attachments." />
            </div>

            <div v-if="attachmentsSorted.length === 0" class="empty">No files yet.</div>

            <div v-else class="list">
              <div v-for="a in attachmentsSorted" :key="a.id" class="item">
                <div class="itemTop">
                  <span class="who">
                    <b>v{{ a.versionNo }}</b> —
                    <span
                      class="docTypeBadge docTypeInline"
                      :class="'docType-' + docTypeClass(resolveAttachmentTypeFromName(a.fileName))"
                      :title="`Attachment type: ${resolveAttachmentTypeFromName(a.fileName)}`"
                    >
                      <component
                        :is="attachmentIconComponent(resolveAttachmentTypeFromName(a.fileName))"
                        class="docIcon"
                        aria-hidden="true"
                      />
                    </span>
                    {{ a.fileName }}
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
        <div class="card timelineCard">
          <div class="cardTitle">Movement Timeline</div>
          <div class="cardSub">Track every workflow handoff and action history.</div>

          <div v-if="!canViewHistory" class="lockBox">
              Only the <b>user currently shown in Report At</b> can view movement history.
          </div>

          <template v-else>
            <div v-if="movements.length === 0" class="empty">No movements yet.</div>
            <div v-else class="list">
              <div
                v-for="m in movements"
                :key="m.id"
                class="item movementItem"
                :class="{ active: selectedMovementId === m.id }"
                @click="toggleMovement(m.id)"
              >
                <div class="itemTop">
                  <span class="who">
                    <b>{{ displayMovementActionLabel(m.actionType) }}</b>
                    <span v-if="m.fromUserId"> | from {{ formatUserLabelById(m.fromUserId, users) }}</span>
                    <span v-if="m.toUserId"> → to {{ formatUserLabelById(m.toUserId, users) }}</span>
                  </span>
                  <span class="when mono">{{ formatDateTime(m.actionAt) }}</span>
                </div>
                <div class="smallHint">Action by: {{ formatUserLabelById(m.actionByUserId, users) }}</div>
                <div v-if="String(m.actionType).toUpperCase() === 'FORWARD'" class="smallHint">
                  Visibility: <b>{{ m.forwardVisibility || "PUBLIC" }}</b>
                </div>

                <div v-if="selectedMovementId === m.id" class="timelineRemarks">
                  <div class="timelineRemarksTitle">Minutes for this movement</div>

                  <div v-if="!canViewRemarks" class="smallHint">
                    Minutes are hidden for your role on this document.
                  </div>

                  <div v-else-if="getRemarksForMovement(m.id).length === 0" class="smallHint">
                    No minute linked to this movement.
                  </div>

                  <div v-else class="list timelineRemarksList">
                    <div v-for="r in getRemarksForMovement(m.id)" :key="r.id" class="item timelineRemarkItem">
                      <div class="itemTop">
                        <span class="who">By <b>{{ formatUserLabelById(r.remarkedByUserId, users) }}</b></span>
                        <span class="when mono">{{ formatDateTime(r.remarkedAt) }}</span>
                      </div>
                      <div class="text">{{ r.remarkText }}</div>
                    </div>
                  </div>
                </div>
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
                <span>
                  <b>v{{ f.versionNo }}</b>
                  <span
                    class="docTypeBadge docTypeInline"
                    :class="'docType-' + docTypeClass(resolveAttachmentTypeFromName(f.fileName))"
                    :title="`Attachment type: ${resolveAttachmentTypeFromName(f.fileName)}`"
                  >
                    <component
                      :is="attachmentIconComponent(resolveAttachmentTypeFromName(f.fileName))"
                      class="docIcon"
                      aria-hidden="true"
                    />
                  </span>
                  {{ f.fileName }}
                </span>
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
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { File, FileText, FileSpreadsheet, Image, Archive } from "lucide-vue-next";
import AppLayout from "../layouts/AppLayout.vue";
import HoverHint from "../components/HoverHint.vue";
import { useToast } from "../composables/useToast";
import { getCurrentUser, hasPermission } from "../auth/currentUser";
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
  getWorkflowRules,
} from "../api/documents.api";

const route = useRoute();
const router = useRouter();
const toast = useToast();
const documentId = Number(route.params.id);

const currentUser = ref(getCurrentUser());
function refreshCurrentUser() {
  currentUser.value = getCurrentUser();
  loadWorkflowRules();
}

const users = ref([]);
const forwardReturnAllowedStatuses = ref(["PENDING", "IN_PROGRESS", "RETURNED"]);
const approveRejectButtonsEnabled = ref(true);

const doc = ref(null);
const movements = ref([]);
const remarks = ref([]);
const attachments = ref([]);
const selectedMovementId = ref(null);

const error = ref("");
const successMessage = ref("");
const busy = ref(false);

const isEditingDetails = ref(false);
const detailsForm = ref({
  refNo: "",
  title: "",
  companyName: "",
  receivedDate: "",
  priority: "MEDIUM",
});

const toUserId = ref(null);
const forwardVisibility = ref("PUBLIC");
const forwardUserSearch = ref("");
const forwardSearchFocused = ref(false);

// ✅ ONE remark box
const remarkDraft = ref("");

const pickedFile = ref(null);
const fileInputRef = ref(null);

const viewerOpen = ref(false);
const selectedFile = ref(null);
const viewerSearch = ref("");

const isOwner = computed(() => !!doc.value && Number(doc.value.currentOwnerUserId) === Number(currentUser.value.id));
const canViewAllHistory = computed(() => hasPermission(currentUser.value, "VIEW_ALL_HISTORY"));
const canViewRemarks = computed(() => !!doc.value && (isOwner.value || hasPermission(currentUser.value, "VIEW_REMARKS_WHEN_NOT_REPORT_AT")));
const isIssued = computed(() => !!doc.value && doc.value.status === "ISSUED");
const isEditLocked = computed(() => !!doc.value && (!!doc.value.completedAt || isIssued.value));
const canEditDetails = computed(() => !!doc.value && isOwner.value && !isEditLocked.value && hasPermission(currentUser.value, "EDIT_DOCUMENT_DETAILS"));

// Keep your existing “history” rule for files/movements
const canViewHistory = computed(() => !!doc.value && (isOwner.value || canViewAllHistory.value));

// Upload: only current owner
const canUploadAttachments = computed(() => !!doc.value && isOwner.value && !isIssued.value && hasPermission(currentUser.value, "UPLOAD_ATTACHMENT"));

// Actions
const canForwardPublic = computed(() => hasPermission(currentUser.value, "FORWARD_PUBLIC"));
const canForwardPrivate = computed(() => hasPermission(currentUser.value, "FORWARD_PRIVATE"));
const availableForwardVisibilities = computed(() => {
  const options = [];
  if (canForwardPublic.value) options.push("PUBLIC");
  if (canForwardPrivate.value) options.push("PRIVATE");
  return options;
});
const canForwardReturnByStatus = computed(() => !!doc.value && forwardReturnAllowedStatuses.value.includes(String(doc.value.status || "").toUpperCase()));
const forwardReturnAllowedStatusesLabel = computed(() => forwardReturnAllowedStatuses.value.map(displayStatusLabel).join(", ") || "none");
const canForward = computed(() => !!doc.value && canForwardReturnByStatus.value && isOwner.value && hasPermission(currentUser.value, "FORWARD_DOCUMENT") && availableForwardVisibilities.value.length > 0);
const canReturn  = computed(() => !!doc.value && canForwardReturnByStatus.value && isOwner.value && hasPermission(currentUser.value, "RETURN_DOCUMENT"));
const canChooseWorkflowTarget = computed(() => canForward.value || canReturn.value);

const canApprove = computed(() => approveRejectButtonsEnabled.value && doc.value && !isIssued.value && isOwner.value && hasPermission(currentUser.value, "APPROVE_DOCUMENT") && doc.value.status !== "APPROVED");
const canReject  = computed(() => approveRejectButtonsEnabled.value && doc.value && !isIssued.value && isOwner.value && hasPermission(currentUser.value, "REJECT_DOCUMENT") && doc.value.status !== "REJECTED");
const canIssue   = computed(() => {
  if (!doc.value || !isOwner.value || !hasPermission(currentUser.value, "ISSUE_DOCUMENT") || !!doc.value.issuedAt) return false;
  return approveRejectButtonsEnabled.value
    ? doc.value.status === "APPROVED"
    : doc.value.status !== "ISSUED";
});
const canReopen  = computed(() => {
  if (!doc.value || !isOwner.value || !hasPermission(currentUser.value, "REOPEN_DOCUMENT")) return false;
  return approveRejectButtonsEnabled.value
    ? !isIssued.value && ["APPROVED","REJECTED"].includes(doc.value.status)
    : ["ISSUED", "APPROVED", "REJECTED"].includes(doc.value.status);
});
const availableWorkflowActionNames = computed(() => {
  const names = ["Forward", "Return"];
  if (approveRejectButtonsEnabled.value) {
    names.push("Approve", "Reject");
  }
  names.push("Done", "Reopen");
  return names.join("/");
});
const workflowRulesHint = computed(() => approveRejectButtonsEnabled.value
    ? "All workflow actions require the document to be assigned to you in Report At, plus the corresponding permission. Done is available only when status is APPROVED (it marks the document as ISSUED). Reopen is allowed only for APPROVED or REJECTED and cannot be done after ISSUED."
    : "All workflow actions require the document to be assigned to you in Report At, plus the corresponding permission. Approve and Reject are hidden by admin workflow settings. Done can complete documents without a prior approval step, and Reopen can reopen Done documents.");

const daysOpenDisplay = computed(() => {
  const received = doc.value?.receivedDate;
  if (!received) return "-";

  const start = new Date(received);
  if (Number.isNaN(start.getTime())) return "-";

  const dayMs = 24 * 60 * 60 * 1000;
  const diff = Math.floor((Date.now() - start.getTime()) / dayMs);
  return String(Math.max(0, diff));
});

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
const canAddRemark = computed(() => !!doc.value && isOwner.value && !isIssued.value && hasPermission(currentUser.value, "ADD_REMARK"));

// textarea allowed if owner can act/save
const canTypeRemark = computed(() => canAddRemark.value || canForward.value || canReturn.value || canApprove.value || canReject.value || canIssue.value || canReopen.value);

const forwardTargets = computed(() => {
  const all = users.value.filter((u) => Number(u.id) !== Number(currentUser.value.id));
  return all;
});

const preferredReturnTargetId = computed(() => {
  if (!canReturn.value || !currentUser.value?.id || forwardTargets.value.length === 0) return null;

  const validIds = new Set(forwardTargets.value.map((u) => Number(u.id)));
  for (let index = movements.value.length - 1; index >= 0; index -= 1) {
    const movement = movements.value[index];
    const actionType = String(movement?.actionType || "").toUpperCase();
    const toUserId = Number(movement?.toUserId);
    const fromUserId = Number(movement?.fromUserId);
    if (!["FORWARD", "RETURN"].includes(actionType)) continue;
    if (toUserId !== Number(currentUser.value.id)) continue;
    if (!Number.isFinite(fromUserId) || !validIds.has(fromUserId)) continue;
    return fromUserId;
  }

  return null;
});

const selectedForwardUser = computed(() => {
  return forwardTargets.value.find((u) => Number(u.id) === Number(toUserId.value)) || null;
});

const filteredForwardTargets = computed(() => {
  const q = forwardUserSearch.value.trim().toLowerCase();
  if (!q) return forwardTargets.value;

  return forwardTargets.value.filter((u) => {
    const searchableText = [
      formatUserLabel(u),
      u.username,
      u.fullName,
      u.name,
      u.role,
      u.department,
      u.id,
    ]
      .filter((part) => part !== null && part !== undefined && String(part).trim() !== "")
      .join(" ")
      .toLowerCase();

    return searchableText.includes(q);
  });
});

const showForwardSearchDropdown = computed(() => {
  return canChooseWorkflowTarget.value && forwardSearchFocused.value && (forwardUserSearch.value.trim() || filteredForwardTargets.value.length > 0);
});

const autoSelectedTargetId = ref(null);

function selectForwardUser(user) {
  if (!user) return;
  toUserId.value = Number(user.id);
  forwardUserSearch.value = formatUserLabel(user);
  forwardSearchFocused.value = false;
  autoSelectedTargetId.value = null;
}

// Keep the workflow target valid and default Return to the most recent sender.
watch(
  [forwardTargets, preferredReturnTargetId, canChooseWorkflowTarget],
  ([list, preferredReturnId, canChooseTarget]) => {
    if (!canChooseTarget) {
      toUserId.value = null;
      autoSelectedTargetId.value = null;
      return;
    }

    const valid = new Set(list.map((x) => Number(x.id)));
    const cur = toUserId.value;
    const desiredTargetId = preferredReturnId != null
      ? Number(preferredReturnId)
      : (list[0] ? Number(list[0].id) : null);
    const currentTargetId = cur == null ? null : Number(cur);
    const currentIsValid = currentTargetId != null && valid.has(currentTargetId);
    const shouldAutoSelect = !currentIsValid || (autoSelectedTargetId.value != null && Number(autoSelectedTargetId.value) === currentTargetId);

    if (shouldAutoSelect) {
      toUserId.value = desiredTargetId;
      autoSelectedTargetId.value = desiredTargetId;
    } else {
      toUserId.value = currentTargetId;
    }
  },
  { immediate: true }
);

watch(
  availableForwardVisibilities,
  (list) => {
    const current = String(forwardVisibility.value || "").toUpperCase();
    if (!list.includes(current)) {
      forwardVisibility.value = list[0] || "PUBLIC";
    }
  },
  { immediate: true }
);

const attachmentsSorted = computed(() => [...attachments.value].sort((a, b) => Number(a.versionNo) - Number(b.versionNo)));

const mainFile = computed(() => {
  if (!attachmentsSorted.value.length) return null;
  return attachmentsSorted.value.find(a => Number(a.versionNo) === 1) || attachmentsSorted.value[0];
});

const mainAttachmentType = computed(() => {
  if (mainFile.value?.fileName) {
    return resolveAttachmentTypeFromName(mainFile.value.fileName);
  }

  const fallback = String(doc.value?.mainAttachmentType || "FILE").toUpperCase();
  return ["PDF", "DOC", "XLS", "IMG", "TXT", "ZIP"].includes(fallback) ? fallback : "FILE";
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

const movementRemarksById = computed(() => {
  const result = new Map();
  for (const m of movements.value) {
    result.set(m.id, []);
  }

  for (const r of remarks.value) {
    const remarkTime = parseDateMs(r.remarkedAt);
    if (remarkTime == null) continue;

    let bestMovement = null;
    let bestDelta = Number.POSITIVE_INFINITY;

    for (const m of movements.value) {
      if (Number(m.actionByUserId) !== Number(r.remarkedByUserId)) continue;

      const actionTime = parseDateMs(m.actionAt);
      if (actionTime == null) continue;

      const delta = actionTime - remarkTime;
      if (delta < 0) continue;

      if (delta <= 10 * 60 * 1000 && delta < bestDelta) {
        bestDelta = delta;
        bestMovement = m;
      }
    }

    if (bestMovement) {
      result.get(bestMovement.id).push(r);
    }
  }

  return result;
});

function isPdf(name) {
  return (name || "").toLowerCase().endsWith(".pdf");
}
function isImage(name) {
  const n = (name || "").toLowerCase();
  return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif") || n.endsWith(".webp");
}

function resolveAttachmentTypeFromName(fileName) {
  const lower = String(fileName || "").toLowerCase();
  if (lower.endsWith(".pdf")) return "PDF";
  if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "DOC";
  if (lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv")) return "XLS";
  if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp")) return "IMG";
  if (lower.endsWith(".txt")) return "TXT";
  if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")) return "ZIP";
  return "FILE";
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

function displayStatusLabel(statusValue) {
  return String(statusValue || "").toUpperCase() === "ISSUED" ? "DONE" : statusValue;
}

function displayMovementActionLabel(actionType) {
  return String(actionType || "").toUpperCase() === "ISSUE" ? "DONE" : actionType;
}

function isDateOnlyValue(value) {
  return typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value.trim());
}

function parseDateMs(value) {
  if (!value) return null;
  const t = new Date(value).getTime();
  return Number.isNaN(t) ? null : t;
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

function toggleMovement(movementId) {
  selectedMovementId.value = selectedMovementId.value === movementId ? null : movementId;
}

function getRemarksForMovement(movementId) {
  return movementRemarksById.value.get(movementId) || [];
}

function remarkOrNull() {
  const t = remarkDraft.value.trim();
  return t ? t : null;
}

function ensureWorkflowMinuteAllowed(actionLabel) {
  if (!remarkDraft.value.trim()) return true;
  if (canAddRemark.value) return true;

  error.value = `You entered a minute, but your role does not have permission to save minutes during ${actionLabel}. Clear the minute and try again, or ask an admin for Add Minute permission.`;
  toast.warning(error.value, 5200);
  return false;
}

function toWorkflowGuidance(message, actionLabel) {
  const text = String(message || "").trim();
  if (!text) return `${actionLabel} failed. Please try again.`;

  if (/not allowed to add minutes/i.test(text)) {
    return `This ${actionLabel.toLowerCase()} was blocked because the minute box contains text and your role cannot save minutes. Clear the minute and try again, or ask an admin for Add Minute permission.`;
  }
  if (/not allowed to complete documents/i.test(text)) {
    return "Done is blocked because your role does not have the Done permission. Ask an admin to enable Done Document permission for your role.";
  }
  if (/approve action is disabled by admin workflow settings/i.test(text)) {
    return "Approve is hidden by admin workflow settings for this workflow. Use the visible actions on this page instead.";
  }
  if (/reject action is disabled by admin workflow settings/i.test(text)) {
    return "Reject is hidden by admin workflow settings for this workflow. Use the visible actions on this page instead.";
  }
  if (/not allowed to approve documents/i.test(text)) {
    return "Approve is blocked because your role does not have the Approve permission. Ask an admin to enable APPROVE_DOCUMENT for your role.";
  }
  if (/not allowed to reject documents/i.test(text)) {
    return "Reject is blocked because your role does not have the Reject permission. Ask an admin to enable REJECT_DOCUMENT for your role.";
  }
  if (/not allowed to forward documents/i.test(text)) {
    return "Forward is blocked because your role does not have the Forward permission. Ask an admin to enable FORWARD_DOCUMENT for your role.";
  }
  if (/not allowed to return documents/i.test(text)) {
    return "Return is blocked because your role does not have the Return permission. Ask an admin to enable RETURN_DOCUMENT for your role.";
  }
  if (/only the current owner/i.test(text)) {
    return `${actionLabel} is available only to the user currently shown in Report At for this document. Ask that user to act, or have the document routed to you first.`;
  }
  if (/must be approved first|document must be approved first/i.test(text)) {
    return "Done is available only after the document has been approved. Approve it first, then try Done again.";
  }
  if (/already issued/i.test(text)) {
    return "This document is already marked Done, so there is nothing more to complete.";
  }
  if (/rejected document/i.test(text) && /issue/i.test(text)) {
    return "Done cannot be used on a rejected document. Reopen or reprocess the document first if it needs more work.";
  }
  return text;
}

async function reloadAll() {
  error.value = "";
  busy.value = true;
  try {
    doc.value = await getDocument(documentId);

    remarks.value = canViewRemarks.value ? await listRemarks(documentId) : [];

    // Keep your history lock only for movements/files
    if (canViewHistory.value) {
      movements.value = await listMovements(documentId);
      attachments.value = await listAttachments(documentId);

      const movementIds = new Set(movements.value.map((m) => m.id));
      if (!movementIds.has(selectedMovementId.value)) {
        selectedMovementId.value = null;
      }
    } else {
      movements.value = [];
      attachments.value = [];
      selectedMovementId.value = null;
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

async function loadWorkflowRules() {
  try {
    const rules = await getWorkflowRules();
    if (Array.isArray(rules?.forwardReturnAllowedStatuses) && rules.forwardReturnAllowedStatuses.length > 0) {
      forwardReturnAllowedStatuses.value = rules.forwardReturnAllowedStatuses.map((status) => String(status).toUpperCase());
    }
    approveRejectButtonsEnabled.value = rules?.approveRejectButtonsEnabled !== false;
  } catch {
    forwardReturnAllowedStatuses.value = ["PENDING", "IN_PROGRESS", "RETURNED"];
    approveRejectButtonsEnabled.value = true;
  }
}

onMounted(async () => {
  window.addEventListener("rms_auth_changed", refreshCurrentUser);
  window.addEventListener("rms_permissions_updated", refreshCurrentUser);
  try {
    users.value = await listUsers();
  } catch {
    users.value = [];
  }
  await loadWorkflowRules();
  await reloadAll();
});

onUnmounted(() => {
  window.removeEventListener("rms_auth_changed", refreshCurrentUser);
  window.removeEventListener("rms_permissions_updated", refreshCurrentUser);
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
    receivedDate: String(doc.value.receivedDate || ""),
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
    toast.error(error.value);
    return;
  }

  const payload = {
    refNo: String(detailsForm.value.refNo || "").trim(),
    title: String(detailsForm.value.title || "").trim(),
    companyName: String(detailsForm.value.companyName || "").trim(),
    receivedDate: String(detailsForm.value.receivedDate || "").trim(),
    priority: detailsForm.value.priority,
  };

  if (!payload.refNo) {
    error.value = "Ref No is required.";
    toast.warning(error.value);
    return;
  }
  if (!payload.title) {
    error.value = "Title is required.";
    toast.warning(error.value);
    return;
  }
  if (!payload.companyName) {
    error.value = "Company is required.";
    toast.warning(error.value);
    return;
  }
  if (!payload.receivedDate) {
    error.value = "Received date is required.";
    toast.warning(error.value);
    return;
  }
  if (!payload.priority) {
    error.value = "Priority is required.";
    toast.warning(error.value);
    return;
  }

  busy.value = true;
  try {
    await updateDocument(documentId, payload);
    isEditingDetails.value = false;
    await reloadAll();
    successMessage.value = "Document details updated successfully.";
    toast.success(successMessage.value);
  } catch (e) {
    error.value = e?.message || "Failed to update details.";
    toast.error(error.value);
  } finally {
    busy.value = false;
  }
}

  // Manual save minute only
  async function saveRemarkOnly() {
    error.value = "";
    const text = remarkDraft.value.trim();
    if (!text) {
      toast.warning("Type a minute before saving.");
      return;
    }

  busy.value = true;
  try {
    await addRemark(documentId, {
      remarkText: text,
    });
    remarkDraft.value = "";
    await reloadAll();
    successMessage.value = "Minute saved successfully.";
    toast.success(successMessage.value);
  } catch (e) {
    error.value = e?.message || "Save minute failed.";
    toast.error(error.value);
  } finally {
    busy.value = false;
  }
}

async function doForward() {
  error.value = "";
  if (!ensureWorkflowMinuteAllowed("Forward")) return;
  if (!toUserId.value) {
    error.value = "Please select a user to forward.";
    toast.warning(error.value);
    return;
  }
  const selectedVisibility = String(forwardVisibility.value || "").toUpperCase();
  if (!["PRIVATE", "PUBLIC"].includes(selectedVisibility)) {
    error.value = "Please select a valid forward visibility.";
    toast.warning(error.value);
    return;
  }
  if (!availableForwardVisibilities.value.includes(selectedVisibility)) {
    error.value = "You do not have permission for selected forward visibility.";
    toast.warning(error.value);
    return;
  }

  busy.value = true;
  try {
    await forwardDocument(documentId, {
      toUserId: Number(toUserId.value),
      forwardVisibility: selectedVisibility,
      remarkText: remarkOrNull(), // ✅ this is what backend expects
    });

    // Forward succeeded. The document may no longer be viewable by this user after ownership change.
    remarkDraft.value = "";
    successMessage.value = "Document forwarded successfully.";
    toast.success(successMessage.value);
    router.push("/inbox");
  } catch (e) {
    error.value = toWorkflowGuidance(e?.message || "Forward failed.", "Forward");
    toast.error(error.value);
  } finally {
    busy.value = false;
  }
}

async function doReturn() {
  error.value = "";
  if (!ensureWorkflowMinuteAllowed("Return")) return;
  if (!toUserId.value) {
    error.value = "Please select a user to return.";
    toast.warning(error.value);
    return;
  }
  busy.value = true;
  try {
    await returnDocument(documentId, {
      toUserId: Number(toUserId.value),
      remarkText: remarkOrNull(),
    });
    remarkDraft.value = "";
    await reloadAll();
    successMessage.value = "Document returned successfully.";
    toast.success(successMessage.value);
  } catch (e) {
    error.value = toWorkflowGuidance(e?.message || "Return failed.", "Return");
    toast.error(error.value);
  } finally {
    busy.value = false;
  }
}

async function doApprove() {
  error.value = "";
  if (!ensureWorkflowMinuteAllowed("Approve")) return;
  busy.value = true;
  try {
    await approveDocument(documentId, { remarkText: remarkOrNull() });
    remarkDraft.value = "";
    await reloadAll();
    successMessage.value = "Document approved successfully.";
    toast.success(successMessage.value);
  } catch (e) {
    error.value = toWorkflowGuidance(e?.message || "Approve failed.", "Approve");
    toast.error(error.value);
  } finally {
    busy.value = false;
  }
}

async function doReject() {
  error.value = "";
  if (!ensureWorkflowMinuteAllowed("Reject")) return;
  busy.value = true;
  try {
    await rejectDocument(documentId, { remarkText: remarkOrNull() });
    remarkDraft.value = "";
    await reloadAll();
    successMessage.value = "Document rejected successfully.";
    toast.success(successMessage.value);
  } catch (e) {
    error.value = toWorkflowGuidance(e?.message || "Reject failed.", "Reject");
    toast.error(error.value);
  } finally {
    busy.value = false;
  }
}

async function doIssue() {
  error.value = "";
  if (!ensureWorkflowMinuteAllowed("Done")) return;
  busy.value = true;
  try {
    await issueDocument(documentId, { remarkText: remarkOrNull() });
    remarkDraft.value = "";
    await reloadAll();
    successMessage.value = "Document completed successfully.";
    toast.success(successMessage.value);
  } catch (e) {
    error.value = toWorkflowGuidance(e?.message || "Done failed.", "Done");
    toast.error(error.value);
  } finally {
    busy.value = false;
  }
}

async function doReopen() {
  error.value = "";
  if (!ensureWorkflowMinuteAllowed("Reopen")) return;
  const txt = remarkDraft.value.trim();
  if (!txt) {
    error.value = "Reopen requires a reason. Type it in the Minute box first.";
    toast.warning(error.value);
    return;
  }

  busy.value = true;
  try {
    await reopenDocument(documentId, { remarkText: txt });
    remarkDraft.value = "";
    await reloadAll();
    successMessage.value = "Document reopened successfully.";
    toast.success(successMessage.value);
  } catch (e) {
    error.value = toWorkflowGuidance(e?.message || "Reopen failed.", "Reopen");
    toast.error(error.value);
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
  if (!pickedFile.value) {
    toast.warning("Choose a file before uploading.");
    return;
  }

  busy.value = true;
  try {
    await uploadAttachment(documentId, pickedFile.value);
    pickedFile.value = null;
    if (fileInputRef.value) fileInputRef.value.value = "";
    await reloadAll();
    successMessage.value = "Attachment uploaded successfully.";
    toast.success(successMessage.value);
  } catch (e) {
    error.value = e?.message || "Upload failed.";
    toast.error(error.value);
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
    successMessage.value = "Attachment deleted successfully.";
    toast.success(successMessage.value);
  } catch (e) {
    error.value = e?.message || "Delete failed.";
    toast.error(error.value);
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
.typeLabel { margin-left:8px; font-weight:700; font-size:12px; color:#374151; }

.formRow { margin-top:10px; }
.label { font-size:12px; font-weight:800; margin-bottom:6px; color:#374151; }
.input { height:38px; width:100%; border:1px solid #e5e7eb; border-radius:8px; padding:0 10px; outline:none; }
.forwardSearchWrap {
  position:relative;
  margin-bottom:8px;
}
.forwardSearch { background:#f9fafb; }
.forwardSearchDropdown {
  position:absolute;
  z-index:25;
  top:calc(100% + 4px);
  left:0;
  right:0;
  max-height:230px;
  overflow:auto;
  border:1px solid #dbe3ef;
  border-radius:10px;
  background:#fff;
  box-shadow:0 14px 30px rgba(15, 23, 42, 0.14);
  padding:6px;
}
.forwardSearchOption {
  width:100%;
  border:0;
  border-radius:8px;
  background:transparent;
  cursor:pointer;
  display:grid;
  gap:3px;
  padding:9px 10px;
  text-align:left;
}
.forwardSearchOption:hover,
.forwardSearchOption.active {
  background:#eff6ff;
}
.forwardUserName {
  color:#111827;
  font-size:13px;
  font-weight:800;
}
.forwardUserMeta,
.forwardSearchEmpty {
  color:#6b7280;
  font-size:12px;
}
.forwardSearchEmpty {
  padding:10px;
}
.forwardSelected {
  display:grid;
  gap:3px;
  margin-top:8px;
  padding:9px 10px;
  border:1px solid #dbeafe;
  border-radius:8px;
  background:#eff6ff;
  color:#1e3a8a;
  font-size:12px;
}
.forwardSelected span {
  color:#64748b;
  font-weight:700;
}
.forwardSelected b {
  color:#111827;
  font-size:13px;
}
.forwardSelected.muted {
  display:block;
  color:#6b7280;
  background:#f9fafb;
  border-color:#e5e7eb;
}
.forwardSearchMeta {
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:10px;
  margin-top:6px;
  color:#6b7280;
  font-size:12px;
}
.linkBtn {
  border:0;
  background:transparent;
  color:#2563eb;
  cursor:pointer;
  font-size:12px;
  font-weight:700;
  padding:0;
}
.linkBtn:hover:not(:disabled) { text-decoration:underline; }
.linkBtn:disabled { opacity:0.6; cursor:not-allowed; }
.textarea { width:100%; min-height:80px; border:1px solid #e5e7eb; border-radius:8px; padding:10px; outline:none; resize:vertical; }
.smallHint { margin-top:6px; font-size:12px; color:#6b7280; }

.hintInline {
  margin-top: 6px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.hintLabel {
  font-size: 12px;
  color: #6b7280;
}

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

.movementItem { cursor:pointer; transition: border-color 0.15s ease, box-shadow 0.15s ease, background-color 0.15s ease; }
.movementItem:hover { border-color:#cbd5e1; background:#f8fafc; }
.movementItem.active { border-color:#93c5fd; background:#eff6ff; box-shadow:0 0 0 2px rgba(59,130,246,0.14); }

.timelineRemarks {
  margin-top:10px;
  padding-top:10px;
  border-top:1px dashed #bfdbfe;
}
.timelineRemarksTitle {
  font-size:12px;
  font-weight:800;
  color:#1d4ed8;
  margin-bottom:6px;
}
.timelineRemarksList { margin-top:6px; }
.timelineRemarkItem {
  background:#fff;
  border-color:#dbeafe;
}

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
  vertical-align:middle;
}
.docTypeInline { margin:0 6px; }
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
.typeLabel { margin-left:8px; font-weight:700; font-size:12px; color:#374151; }

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
  vertical-align:middle;
}
.docTypeInline { margin:0 6px; }
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

/* Polished document workspace */
.topbar {
  background:
    radial-gradient(90% 120% at 100% 0%, rgba(37, 99, 235, 0.12), transparent 58%),
    linear-gradient(135deg, #ffffff 0%, #f8fafc 100%);
  border:1px solid #e5e7eb;
  border-radius:14px;
  padding:16px;
  box-shadow:0 8px 24px rgba(17, 24, 39, 0.06);
}

.title {
  color:#0f172a;
  letter-spacing:-0.02em;
}

.meta .pill {
  background:#f8fafc;
  color:#1f2937;
  border-color:#dbe3ef;
  font-weight:800;
}

.meta .pill:first-child {
  background:#eff6ff;
  color:#1d4ed8;
  border-color:#bfdbfe;
}

.subMeta {
  flex-wrap:wrap;
}

.grid {
  align-items:start;
}

.card {
  border-radius:14px;
  border-color:#e2e8f0;
  box-shadow:0 8px 22px rgba(17, 24, 39, 0.045);
}

.cardTitle {
  margin-bottom:2px;
  color:#0f172a;
  font-size:16px;
  letter-spacing:-0.01em;
}

.cardSub {
  color:#6b7280;
  font-size:12px;
  line-height:1.45;
  margin-bottom:12px;
}

.detailsCard .kv {
  grid-template-columns: 170px minmax(0, 1fr);
  gap:0;
  overflow:hidden;
  border:1px solid #eef2f7;
  border-radius:12px;
}

.detailsCard .k,
.detailsCard .v {
  padding:10px 12px;
  border-bottom:1px solid #eef2f7;
}

.detailsCard .k {
  background:#f8fafc;
  color:#64748b;
}

.detailsCard .v {
  min-width:0;
  background:#fff;
  overflow-wrap:anywhere;
}

.detailsCard .k:nth-last-child(2),
.detailsCard .v:last-child {
  border-bottom:0;
}

.workflowCard {
  border-color:#bfdbfe;
  background:
    linear-gradient(180deg, #ffffff 0%, #f8fbff 100%),
    #fff;
}

.ownershipBanner {
  display:grid;
  grid-template-columns:1fr 1fr;
  gap:10px;
  margin:12px 0 14px;
}

.ownershipBanner > div {
  display:grid;
  gap:4px;
  padding:11px 12px;
  border:1px solid #fecaca;
  border-radius:12px;
  background:#fef2f2;
}

.ownershipBanner.owner > div {
  border-color:#bfdbfe;
  background:#eff6ff;
}

.ownershipLabel {
  color:#64748b;
  font-size:11px;
  font-weight:900;
  letter-spacing:0.07em;
  text-transform:uppercase;
}

.ownershipBanner b {
  color:#111827;
  font-size:13px;
}

.formRow {
  margin-top:14px;
}

.label {
  letter-spacing:0.01em;
}

.input,
.textarea,
.search {
  transition:border-color 0.15s ease, box-shadow 0.15s ease, background-color 0.15s ease;
}

.input:focus,
.textarea:focus,
.search:focus {
  border-color:#93c5fd;
  box-shadow:0 0 0 3px rgba(37, 99, 235, 0.12);
}

.textarea {
  min-height:96px;
  line-height:1.5;
}

.forwardSelected {
  border-color:#bfdbfe;
}

.btn {
  font-weight:800;
  transition:background-color 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease, transform 0.12s ease;
}

.btn:hover:not(:disabled) {
  transform:translateY(-1px);
  box-shadow:0 8px 18px rgba(17, 24, 39, 0.08);
}

.btnRow .spacer + .btn {
  border-color:#bfdbfe;
  color:#1d4ed8;
}

.btnRow .spacer + .btn + .btn {
  border-color:#fecaca;
  color:#991b1b;
}

.btnRow .spacer + .btn + .btn + .btn,
.btnRow .spacer + .btn + .btn + .btn + .btn {
  color:#374151;
}

.filesCard .fileRow {
  padding:12px;
  border:1px solid #eef2f7;
  border-radius:12px;
  background:#f8fafc;
}

.miniPreview {
  overflow:hidden;
  border-radius:12px;
}

.attachRow {
  padding:12px;
  border:1px dashed #cbd5e1;
  border-radius:12px;
  background:#f9fafb;
}

.item {
  background:#fff;
  border-color:#e2e8f0;
  box-shadow:0 4px 12px rgba(17, 24, 39, 0.035);
}

.minutesCard .item {
  border-left:4px solid #2563eb;
}

.empty {
  border:1px dashed #d1d5db;
  border-radius:10px;
  background:#f9fafb;
  text-align:center;
}

.lockBox,
.errorBox,
.successBox {
  box-shadow:0 6px 16px rgba(17, 24, 39, 0.04);
}

@media (max-width: 1100px) {
  .grid {
    grid-template-columns:1fr;
  }

  .rightBtns {
    justify-content:flex-start;
  }
}

@media (max-width: 720px) {
  .topbar,
  .card {
    border-radius:12px;
  }

  .topbar,
  .cardHead,
  .fileRow,
  .viewerHead {
    flex-direction:column;
    align-items:stretch;
  }

  .detailsCard .kv {
    grid-template-columns:1fr;
  }

  .detailsCard .k {
    border-bottom:0;
    padding-bottom:3px;
  }

  .detailsCard .v {
    padding-top:3px;
  }

  .ownershipBanner {
    grid-template-columns:1fr;
  }

  .btnRow,
  .rightBtns,
  .viewerBtns,
  .attachRow {
    align-items:stretch;
    flex-direction:column;
  }

  .btn {
    width:100%;
  }

  .itemTop {
    flex-direction:column;
  }
}

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
