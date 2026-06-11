<template>
  <AppLayout>
    <div class="inboxCanvas">
      <div class="pageHead">
        <div class="titleBlock">
          <h2>My Inbox</h2>
          <p class="pageSub">
            <template v-if="inboxMode === 'received'">Scan assigned documents in a message-style view and open items faster.</template>
            <template v-else>Track documents you forwarded and quickly open them again.</template>
          </p>
        </div>
        <div class="headActions">
          <button class="btn" @click="load">Refresh</button>
        </div>
      </div>

      <div class="modeTabs card">
        <button class="modeTab" :class="{ modeTabActive: inboxMode === 'received' }" @click="setMode('received')">Received</button>
        <button
          class="modeTab"
          :class="{ modeTabActive: inboxMode === 'sent' }"
          :disabled="!canViewSentMessages"
          @click="setMode('sent')"
        >
          Sent
        </button>
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
              <option value="ISSUED">DONE</option>
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
          <button
            v-if="inboxMode === 'received'"
            class="chip"
            :class="{ chipActive: viewFilter === 'unopened' }"
            @click="viewFilter = 'unopened'"
          >
            Unopened
          </button>
          <button
            v-if="inboxMode === 'received'"
            class="chip"
            :class="{ chipActive: viewFilter === 'opened' }"
            @click="viewFilter = 'opened'"
          >
            Opened
          </button>
          <button class="chip" :class="{ chipActive: viewFilter === 'urgent' }" @click="viewFilter = 'urgent'">Urgent</button>
        </div>
      </div>

      <div v-if="error" class="errorBox">{{ error }}</div>

      <div class="card inboxCard">
        <div class="inboxHead">
          <div class="inboxTitleWrap">
            <span class="inboxTitle">{{ inboxMode === 'received' ? 'Received Messages' : 'Sent Messages' }}</span>
            <span class="inboxMeta">{{ rows.length }} item{{ rows.length === 1 ? '' : 's' }}</span>
          </div>
          <div class="tableHintWrap">
            <span class="tableHintLabel">Inbox Info</span>
            <HoverHint :text="inboxMode === 'received'
              ? `${sortHint}. Unopened means the document has not been opened by you in the current assignment.`
              : `${sortHint}. Sent shows documents you forwarded or returned, plus any undo notices sent back to you.`" />
          </div>
        </div>

        <div v-if="loading" class="emptyState">Loading...</div>
        <div v-else-if="rows.length===0" class="emptyState">No inbox items.</div>

        <div v-else class="mailList">
          <article
            v-for="d in rows"
            :key="rowKey(d)"
            class="mailRow"
            :class="{
              unopened: inboxMode === 'received' && !isViewedByMe(d),
              opened: inboxMode === 'received' && isViewedByMe(d),
            }"
            @click="open(resolveDocumentId(d))"
          >
            <div class="mailLeft">
              <span
                class="unreadDot"
                :class="{ visible: inboxMode === 'received' && !isViewedByMe(d) }"
                aria-hidden="true"
              ></span>
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
                <template v-if="inboxMode === 'sent'">
                  <template v-if="undoSendInfo(d).isUndoNotice">
                    <span class="undoSendInfo">{{ undoSendInfo(d).helper }}</span>
                    <br />
                    <span class="sentMetaLine">
                      <template v-if="undoSendInfo(d).isReceiverUndoNotice">
                        Document returned to {{ sentToLabel(d) }}
                      </template>
                      <template v-else>
                        Send was undone
                      </template>
                      • {{ displaySentDate(d) }}
                    </span>
                  </template>
                  <template v-else-if="d.latestRemarkPreview">
                    Your latest minute: {{ d.latestRemarkPreview }}
                    <br />
                    <span class="sentMetaLine">
                      Sent to {{ sentToLabel(d) }}<span v-if="d.autoForwarded"> (auto-forwarded)</span> • {{ String(d.forwardVisibility || 'PRIVATE').toUpperCase() }} • {{ displaySentDate(d) }}
                    </span>
                    <template v-if="undoSendInfo(d).helper">
                      <br />
                      <span class="undoSendInfo">{{ undoSendInfo(d).helper }}</span>
                    </template>
                  </template>
                  <template v-else>
                    No minute added by you
                    <br />
                    <span class="sentMetaLine">
                      Sent to {{ sentToLabel(d) }}<span v-if="d.autoForwarded"> (auto-forwarded)</span> • {{ String(d.forwardVisibility || 'PRIVATE').toUpperCase() }} • {{ displaySentDate(d) }}
                    </span>
                    <template v-if="undoSendInfo(d).helper">
                      <br />
                      <span class="undoSendInfo">{{ undoSendInfo(d).helper }}</span>
                    </template>
                  </template>
                </template>
                <template v-else>
                  <span :title="inboxReceivedPreview(d).minuteLine ? null : inboxReceivedPreview(d).minuteTooltip">
                    {{ inboxReceivedPreview(d).senderLine }}
                  </span>
                  <template v-if="inboxReceivedPreview(d).minuteLine">
                    <br />
                    <span :title="inboxReceivedPreview(d).minuteTooltip">{{ inboxReceivedPreview(d).minuteLine }}</span>
                  </template>
                  <template v-else-if="inboxReceivedPreview(d).fallbackLine">
                    <br />
                    <span>{{ inboxReceivedPreview(d).fallbackLine }}</span>
                  </template>
                </template>
              </div>
            </div>

            <div class="mailRight">
              <span class="pill" :class="'pill-'+d.status">{{ displayStatusLabel(d.status) }}</span>
              <span class="timeText">{{ displayDate(d) }}</span>
              <div class="rowActions">
                <button
                  type="button"
                  class="iconAction"
                  title="Preview document"
                  aria-label="Preview document"
                  @click.stop="openPreview(d)"
                >
                  <Eye class="actionIcon" aria-hidden="true" />
                </button>
                <button
                  v-if="inboxMode === 'received'"
                  type="button"
                  class="iconAction"
                  :class="{ disabled: !canForwardRow(d) }"
                  :disabled="!canForwardRow(d)"
                  :title="canForwardRow(d) ? 'Forward document' : 'Forward is not available for this document'"
                  :aria-label="canForwardRow(d) ? 'Forward document' : 'Forward not available'"
                  @click.stop="openForwardDialog(d)"
                >
                  <Send class="actionIcon" aria-hidden="true" />
                </button>
                <button
                  v-if="inboxMode === 'sent' && undoSendInfo(d).canUndo"
                  class="btn btn-sm undoSendBtn"
                  :disabled="forwardBusy"
                  @click.stop="doUndoSend(d)"
                >
                  Undo Send
                </button>
                <button class="btn btn-sm" @click.stop="open(resolveDocumentId(d))">Open</button>
              </div>
            </div>
          </article>
        </div>
      </div>

      <div v-if="previewOpen" class="overlay" @click.self="closePreview">
        <div class="modal previewModal fullPreviewModal">
          <div class="modalHead">
            <div>
              <div class="modalEyebrow">Full Screen Preview</div>
              <div class="modalTitle">{{ previewDoc?.refNo || '-' }} - {{ previewDoc?.title || 'Untitled document' }}</div>
              <div class="modalSub">{{ previewDoc?.refNo || '-' }} - {{ previewDoc?.title || 'Untitled document' }}</div>
            </div>
            <div class="previewHeaderActions">
              <div v-if="previewAttachmentsSorted.length > 1" class="fullPreviewToolbar">
                <button
                  type="button"
                  class="miniSwitchBtn"
                  :disabled="!canGoPreviousPreviewAttachment"
                  @click="selectPreviousPreviewAttachment"
                  aria-label="Previous attachment"
                >
                  ‹
                </button>
                <select v-model="selectedPreviewAttachmentId" class="fullPreviewSelect" aria-label="Select attachment preview">
                  <option v-for="a in previewAttachmentsSorted" :key="a.id" :value="a.id">
                    v{{ a.versionNo }} - {{ a.fileName }}
                  </option>
                </select>
                <button
                  type="button"
                  class="miniSwitchBtn"
                  :disabled="!canGoNextPreviewAttachment"
                  @click="selectNextPreviewAttachment"
                  aria-label="Next attachment"
                >
                  ›
                </button>
              </div>
              <button class="btn" @click="openPreviewDocument">Open Full Document</button>
              <button class="iconBtn modalClose" @click="closePreview" aria-label="Close preview">x</button>
            </div>
          </div>

          <div class="modalBody fullPreviewBody">
            <div class="fullPreviewViewer">
              <div v-if="previewLoadingExtras" class="fullPreviewEmpty">Loading document preview...</div>
              <iframe
                v-else-if="selectedPreviewAttachment && isPdfFileName(selectedPreviewAttachment.fileName)"
                :src="previewAttachmentPreviewUrl(selectedPreviewAttachment)"
                class="fullPreviewFrame"
                title="Document PDF preview"
              ></iframe>
              <img
                v-else-if="selectedPreviewAttachment && isImageFileName(selectedPreviewAttachment.fileName)"
                :src="previewAttachmentPreviewUrl(selectedPreviewAttachment)"
                class="fullPreviewImage"
                alt="Document preview"
              />
              <div v-else class="fullPreviewEmpty">
                <span
                  class="docTypeBadge"
                  :class="'docType-' + docTypeClass(selectedPreviewAttachmentType)"
                  :title="attachmentTypeLabel(selectedPreviewAttachmentType)"
                >
                  <component :is="attachmentIconComponent(selectedPreviewAttachmentType)" class="docIcon" aria-hidden="true" />
                </span>
                <b>{{ selectedPreviewAttachmentType }}</b>
                <span>{{ selectedPreviewAttachment?.fileName || 'No attachment available to preview' }}</span>
                <button v-if="selectedPreviewAttachment" class="btn" @click="openAttachmentInNewTab(selectedPreviewAttachment)">Open File</button>
              </div>
            </div>

            <aside class="fullPreviewSide">
              <div class="previewPills">
                <span class="pill" :class="'pill-'+previewDoc?.status">{{ displayStatusLabel(previewDoc?.status) || '-' }}</span>
                <span class="pill" :class="'pill-'+previewDoc?.priority">{{ previewDoc?.priority || '-' }}</span>
              </div>

              <div class="previewGrid">
                <div><span class="label">Company</span>{{ previewDoc?.companyName || '-' }}</div>
                <div><span class="label">Received</span>{{ formatDateSafe(previewDoc?.receivedDate) }}</div>
                <div><span class="label">Days Open</span>{{ previewDaysOpen }}</div>
                <div><span class="label">Viewing File</span>{{ selectedPreviewAttachment?.fileName || selectedPreviewAttachmentType }}</div>
                <div><span class="label">Attachments</span>{{ previewAttachmentCount }}</div>
                <div><span class="label">Report At</span>{{ ownerLabel(previewDoc?.currentOwnerUserId, previewDoc?.currentOwnerName) }}</div>
              </div>

              <div v-if="previewExtrasError" class="note noteWarn">{{ previewExtrasError }}</div>

              <div v-if="previewCanSeeOperational" class="opsCard">
                <div class="opsTitle">Latest Activity</div>
                <div class="opsRow">
                  <span class="label">Last Action</span>
                  <span>
                    {{ displayMovementActionLabel(previewLastMovement?.actionType) || '-' }}
                    <span v-if="previewLastMovement"> • {{ formatDateTimeSafe(previewLastMovement.actionAt) }}</span>
                  </span>
                </div>
                <div class="opsRow">
                  <span class="label">Action By</span>
                  <span>{{ previewLastMovement ? ownerLabel(previewLastMovement.actionByUserId, previewLastMovement.actionByUserName) : '-' }}</span>
                </div>
                <div class="opsRow">
                  <span class="label">Latest Minute</span>
                  <span>{{ previewCanViewRemarks ? (previewLastRemark?.remarkText || 'No minute recorded.') : 'Minutes are available only when this document is assigned to you in Report At.' }}</span>
                </div>
              </div>
            </aside>
          </div>
        </div>
      </div>

      <div v-if="forwardOpen" class="overlay">
        <div class="modal forwardModal">
          <div class="modalHead">
            <div>
              <div class="modalEyebrow">Workflow Shortcut</div>
              <div class="modalTitle">Forward Document</div>
              <div class="modalSub">{{ forwardDoc?.refNo || '-' }} - {{ forwardDoc?.title || 'Untitled document' }}</div>
            </div>
            <button class="iconBtn modalClose" :disabled="forwardBusy" @click="closeForwardDialog" aria-label="Close forward dialog">x</button>
          </div>

          <div class="modalBody">
            <div class="forwardDocPreview">
              <div class="forwardFileBox">
                <div v-if="forwardAttachments.length > 1" class="forwardFileSwitcher">
                  <button
                    type="button"
                    class="miniSwitchBtn"
                    :disabled="!canGoPreviousForwardAttachment"
                    @click="selectPreviousForwardAttachment"
                    aria-label="Previous attachment"
                  >
                    ‹
                  </button>
                  <select
                    v-model="selectedForwardAttachmentId"
                    class="forwardAttachmentSelect"
                    aria-label="Select attachment preview"
                  >
                    <option v-for="a in forwardAttachmentsSorted" :key="a.id" :value="a.id">
                      v{{ a.versionNo }} - {{ a.fileName }}
                    </option>
                  </select>
                  <button
                    type="button"
                    class="miniSwitchBtn"
                    :disabled="!canGoNextForwardAttachment"
                    @click="selectNextForwardAttachment"
                    aria-label="Next attachment"
                  >
                    ›
                  </button>
                </div>
                <div v-if="forwardAttachmentsLoading" class="forwardFileEmpty">Loading file preview...</div>
                <iframe
                  v-else-if="selectedForwardAttachment && isPdfFileName(selectedForwardAttachment.fileName)"
                  :src="forwardAttachmentPreviewUrl(selectedForwardAttachment)"
                  class="forwardMiniFrame"
                  title="Attachment PDF preview"
                ></iframe>
                <img
                  v-else-if="selectedForwardAttachment && isImageFileName(selectedForwardAttachment.fileName)"
                  :src="forwardAttachmentPreviewUrl(selectedForwardAttachment)"
                  class="forwardMiniImage"
                  alt="Attachment preview"
                />
                <div v-else class="forwardFileEmpty">
                  <span
                    class="docTypeBadge"
                    :class="'docType-' + docTypeClass(selectedForwardAttachmentType)"
                    :title="attachmentTypeLabel(selectedForwardAttachmentType)"
                  >
                    <component :is="attachmentIconComponent(selectedForwardAttachmentType)" class="docIcon" aria-hidden="true" />
                  </span>
                  <b>{{ selectedForwardAttachmentType }}</b>
                  <span>{{ selectedForwardAttachment?.fileName || 'No attachment available' }}</span>
                </div>
              </div>

              <div class="forwardDocSummary">
                <div class="summaryTop">
                  <div>
                    <div class="summaryRef">{{ forwardDoc?.refNo || '-' }}</div>
                    <div class="summaryTitle">{{ forwardDoc?.title || 'Untitled document' }}</div>
                  </div>
                  <div class="summaryPills">
                    <span class="pill" :class="'pill-'+forwardDoc?.status">{{ displayStatusLabel(forwardDoc?.status) || '-' }}</span>
                    <span class="pill" :class="'pill-'+forwardDoc?.priority">{{ forwardDoc?.priority || '-' }}</span>
                  </div>
                </div>

                <div class="summaryGrid">
                  <div><span class="label">Company</span>{{ forwardDoc?.companyName || '-' }}</div>
                  <div><span class="label">Received</span>{{ formatDateSafe(forwardDoc?.receivedDate) }}</div>
                  <div><span class="label">Days Open</span>{{ forwardDaysOpen }}</div>
                  <div><span class="label">Viewing File</span>{{ selectedForwardAttachment?.fileName || selectedForwardAttachmentType }}</div>
                  <div><span class="label">Attachments</span>{{ forwardAttachmentCount }}</div>
                  <div><span class="label">Forwarding As</span>{{ formatUserLabel(currentUser) }}</div>
                </div>
              </div>
            </div>

            <div v-if="forwardAttachmentsError" class="note noteWarn">{{ forwardAttachmentsError }}</div>

            <div v-if="!canForwardSelectedDoc && !canReturnSelectedDoc" class="note noteWarn">
              Forward and Return are available only to the user currently shown in Report At with the relevant permission, and allowed statuses are managed from Permissions.
            </div>

            <div class="formRow">
              <label class="label">Minute (optional)</label>
              <textarea
                v-model="forwardRemark"
                class="textarea"
                :disabled="forwardBusy || (!canForwardSelectedDoc && !canReturnSelectedDoc)"
                placeholder="Type minute before forwarding or returning..."
              ></textarea>
            </div>

            <div class="formRow">
              <label class="label">Forward To</label>
              <div class="forwardSearchWrap">
                <input
                  v-model="forwardUserSearch"
                  class="input"
                  :disabled="forwardBusy || !canChooseWorkflowTarget"
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
                    :class="{ active: Number(u.id) === Number(forwardToUserId) }"
                    @mousedown.prevent="selectForwardUser(u)"
                  >
                    <span class="forwardUserName">{{ formatUserLabel(u) }}</span>
                    <span class="forwardUserMeta">{{ u.username || '-' }}<span v-if="u.department"> • {{ u.department }}</span></span>
                  </button>
                  <div v-if="filteredForwardTargets.length === 0" class="forwardSearchEmpty">No matching users</div>
                </div>
              </div>

              <div v-if="selectedForwardUser" class="forwardSelected">
                <span>Selected user</span>
                <b>{{ formatUserLabel(selectedForwardUser) }}</b>
              </div>
              <div v-else class="forwardSelected muted">Select a user before forwarding or returning.</div>

              <div class="forwardSearchMeta">
                <span>{{ forwardUserSearch.trim() ? `${filteredForwardTargets.length} of ${forwardTargets.length} users shown` : `${forwardTargets.length} users available` }}</span>
                <button v-if="forwardUserSearch" type="button" class="linkBtn" :disabled="forwardBusy" @click="clearForwardSearch">
                  Clear search
                </button>
              </div>
            </div>

            <div class="formRow">
              <label class="label">Forward Visibility</label>
              <select v-model="forwardVisibility" class="input" :disabled="forwardBusy || !canForwardSelectedDoc">
                <option v-for="opt in availableForwardVisibilities" :key="opt" :value="opt">
                  {{ opt.charAt(0) + opt.slice(1).toLowerCase() }}
                </option>
              </select>
            </div>
          </div>

          <div class="modalFoot">
            <button class="btn" :disabled="forwardBusy" @click="closeForwardDialog">Cancel</button>
            <button class="btn" :disabled="forwardBusy || !canReturnSelectedDoc || !forwardToUserId" @click="submitReturn">
              {{ forwardBusy ? 'Returning...' : 'Return' }}
            </button>
            <button class="btn btn-primary" :disabled="forwardBusy || !canForwardSelectedDoc || !forwardToUserId" @click="submitForward">
              {{ forwardBusy ? 'Forwarding...' : 'Forward' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from "vue";
import { useRouter } from "vue-router";
import { File, FileText, FileSpreadsheet, Image, Archive, Eye, Send } from "lucide-vue-next";
import AppLayout from "../layouts/AppLayout.vue";
import HoverHint from "../components/HoverHint.vue";
import { useToast } from "../composables/useToast";
import { listUsers } from "../api/auth.api";
import { getAttachmentViewerState, resolveAttachmentTypeFromName } from "../utils/attachmentViewerLogic";
import { formatDateSafe, formatDateTimeSafe } from "../utils/dateFormat";
import {
  forwardDocument,
  getDocument,
  getWorkflowRules,
  createAttachmentDownloadUrl,
  listAttachments,
  listMyInboxDocuments,
  listMovements,
  listRemarks,
  listSentMessages,
  returnDocument,
  undoSendDocument,
} from "../api/documents.api";
import { getCurrentUser, hasPermission } from "../auth/currentUser";
import { formatUserLabel, formatUserLabelFromParts } from "../auth/userLabel";
import { buildInboxReceivedPreview, findPreferredReturnTargetId, markInboxDocumentViewed, resolveWorkflowAutoTarget, sortInboxDefaultDisplay, sortInboxDocumentsBy } from "../utils/inboxLogic";
import { canForwardInboxDocument, canReturnInboxDocument } from "../utils/inboxPermissionLogic";
import { getWorkflowSenderSuccessMessage } from "../utils/workflowNotificationLogic";
import { getUndoSendInfo, needsUndoReason } from "../utils/undoSendLogic";

const router = useRouter();
const toast = useToast();

const loading = ref(false);
const error = ref("");
const allRows = ref([]);
const rows = ref([]);
const q = ref("");
const status = ref("");
const priority = ref("");
const sortBy = ref("recent");
const sortTouched = ref(false);
const viewFilter = ref("all");
const inboxMode = ref("received");
const authTick = ref(0);
const users = ref([]);
const forwardReturnAllowedStatuses = ref(["PENDING", "IN_PROGRESS", "RETURNED"]);

const previewOpen = ref(false);
const previewDoc = ref(null);
const previewLoadingExtras = ref(false);
const previewExtrasError = ref("");
const previewMovements = ref([]);
const previewRemarks = ref([]);
const previewAttachments = ref([]);
const selectedPreviewAttachmentId = ref(null);
const attachmentUrls = ref({});

const forwardOpen = ref(false);
const forwardDoc = ref(null);
const forwardBusy = ref(false);
const forwardRemark = ref("");
const forwardVisibility = ref("PUBLIC");
const forwardToUserId = ref(null);
const forwardUserSearch = ref("");
const forwardSearchFocused = ref(false);
const forwardAttachments = ref([]);
const forwardAttachmentsLoading = ref(false);
const forwardAttachmentsError = ref("");
const selectedForwardAttachmentId = ref(null);
const forwardMovements = ref([]);
const autoSelectedForwardTargetId = ref(null);

const currentUser = computed(() => {
  authTick.value;
  return getCurrentUser();
});
const canViewSentMessages = computed(() => {
  authTick.value;
  return hasPermission(currentUser.value, "VIEW_SENT_MESSAGES");
});

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

const canForwardPublic = computed(() => hasPermission(currentUser.value, "FORWARD_PUBLIC"));
const canForwardPrivate = computed(() => hasPermission(currentUser.value, "FORWARD_PRIVATE"));
const availableForwardVisibilities = computed(() => {
  const options = [];
  if (canForwardPublic.value) options.push("PUBLIC");
  if (canForwardPrivate.value) options.push("PRIVATE");
  return options;
});

const forwardTargets = computed(() => {
  const all = users.value.filter((u) => Number(u.id) !== Number(currentUser.value?.id));
  return all;
});

const canForwardSelectedDoc = computed(() => canForwardRow(forwardDoc.value));
const canReturnSelectedDoc = computed(() => {
  return canReturnInboxDocument({
    doc: forwardDoc.value,
    user: currentUser.value,
    forwardReturnAllowedStatuses: forwardReturnAllowedStatuses.value,
  });
});

const canChooseWorkflowTarget = computed(() => canForwardSelectedDoc.value || canReturnSelectedDoc.value);

const preferredReturnTargetId = computed(() => findPreferredReturnTargetId({
  canReturn: canReturnSelectedDoc.value,
  currentUserId: currentUser.value?.id,
  forwardTargets: forwardTargets.value,
  forwardMovements: forwardMovements.value,
}));

const selectedForwardUser = computed(() => {
  return forwardTargets.value.find((u) => Number(u.id) === Number(forwardToUserId.value)) || null;
});

const filteredForwardTargets = computed(() => {
  const search = forwardUserSearch.value.trim().toLowerCase();
  if (!search) return forwardTargets.value;

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

    return searchableText.includes(search);
  });
});

const showForwardSearchDropdown = computed(() => {
  return (
    canChooseWorkflowTarget.value &&
    forwardSearchFocused.value &&
    (forwardUserSearch.value.trim() || filteredForwardTargets.value.length > 0)
  );
});

const forwardAttachmentViewerState = computed(() => getAttachmentViewerState(forwardAttachments.value, selectedForwardAttachmentId.value));
const forwardAttachmentsSorted = computed(() => forwardAttachmentViewerState.value.sortedAttachments);
const forwardMainAttachment = computed(() => forwardAttachmentViewerState.value.primaryAttachment);
const selectedForwardAttachment = computed(() => forwardAttachmentViewerState.value.selectedAttachment);

const forwardAttachmentCount = computed(() => forwardAttachments.value.length);

const forwardMainAttachmentType = computed(() => {
  if (forwardMainAttachment.value?.fileName) {
    return resolveAttachmentTypeFromName(forwardMainAttachment.value.fileName);
  }
  return docTypeClass(forwardDoc.value?.mainAttachmentType);
});

const selectedForwardAttachmentType = computed(() => {
  if (selectedForwardAttachment.value?.fileName) {
    return resolveAttachmentTypeFromName(selectedForwardAttachment.value.fileName);
  }
  return forwardMainAttachmentType.value;
});

const selectedForwardAttachmentIndex = computed(() => forwardAttachmentViewerState.value.selectedIndex);
const canGoPreviousForwardAttachment = computed(() => forwardAttachmentViewerState.value.canGoPrevious);
const canGoNextForwardAttachment = computed(() => forwardAttachmentViewerState.value.canGoNext);

const forwardDaysOpen = computed(() => {
  const received = forwardDoc.value?.receivedDate;
  if (!received) return "-";

  const start = new Date(received);
  if (Number.isNaN(start.getTime())) return "-";

  const dayMs = 24 * 60 * 60 * 1000;
  return String(Math.max(0, Math.floor((Date.now() - start.getTime()) / dayMs)));
});

const previewIsOwner = computed(() => {
  if (!previewDoc.value || !currentUser.value) return false;
  return Number(previewDoc.value.currentOwnerUserId) === Number(currentUser.value.id);
});

const previewCanViewRemarks = computed(() => {
  if (!previewDoc.value || !currentUser.value) return false;
  return previewIsOwner.value || hasPermission(currentUser.value, "VIEW_REMARKS_WHEN_NOT_REPORT_AT");
});

const previewCanSeeOperational = computed(() => {
  if (!previewDoc.value || !currentUser.value) return false;
  return hasPermission(currentUser.value, "VIEW_ALL_HISTORY") || previewIsOwner.value;
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

const previewAttachmentViewerState = computed(() => getAttachmentViewerState(previewAttachments.value, selectedPreviewAttachmentId.value));
const previewAttachmentsSorted = computed(() => previewAttachmentViewerState.value.sortedAttachments);
const previewMainAttachment = computed(() => previewAttachmentViewerState.value.primaryAttachment);
const selectedPreviewAttachment = computed(() => previewAttachmentViewerState.value.selectedAttachment);

const previewAttachmentCount = computed(() => previewAttachments.value.length);

const previewMainAttachmentType = computed(() => {
  if (previewMainAttachment.value?.fileName) {
    return resolveAttachmentTypeFromName(previewMainAttachment.value.fileName);
  }
  return docTypeClass(previewDoc.value?.mainAttachmentType);
});

const selectedPreviewAttachmentType = computed(() => {
  if (selectedPreviewAttachment.value?.fileName) {
    return resolveAttachmentTypeFromName(selectedPreviewAttachment.value.fileName);
  }
  return previewMainAttachmentType.value;
});

const selectedPreviewAttachmentIndex = computed(() => previewAttachmentViewerState.value.selectedIndex);
const canGoPreviousPreviewAttachment = computed(() => previewAttachmentViewerState.value.canGoPrevious);
const canGoNextPreviewAttachment = computed(() => previewAttachmentViewerState.value.canGoNext);

const previewIsMainFilePreviewable = computed(() => {
  const name = previewMainAttachment.value?.fileName;
  if (!name) return ["PDF", "IMG", "TXT"].includes(previewMainAttachmentType.value);
  return isPreviewableFileName(name);
});

const previewDaysOpen = computed(() => {
  const received = previewDoc.value?.receivedDate;
  if (!received) return "-";

  const start = new Date(received);
  if (Number.isNaN(start.getTime())) return "-";

  const dayMs = 24 * 60 * 60 * 1000;
  return String(Math.max(0, Math.floor((Date.now() - start.getTime()) / dayMs)));
});

function toText(value) {
  return String(value ?? "").trim().toLowerCase();
}

function displayStatusLabel(statusValue) {
  return String(statusValue || "").toUpperCase() === "ISSUED" ? "DONE" : statusValue;
}

function inboxReceivedPreview(doc) {
  return buildInboxReceivedPreview(doc, displayMinuteTime, currentUser.value?.id);
}

function undoSendInfo(doc) {
  return getUndoSendInfo(doc);
}

function sentToLabel(doc) {
  const name = String(doc?.toUserName || "").trim();
  return name || "Unknown user";
}

function displayMovementActionLabel(actionType) {
  return String(actionType || "").toUpperCase() === "ISSUE" ? "DONE" : actionType;
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

function isPreviewableFileName(fileName) {
  const type = resolveAttachmentTypeFromName(fileName);
  return type === "PDF" || type === "IMG" || type === "TXT";
}

function isPdfFileName(fileName) {
  return String(fileName || "").toLowerCase().endsWith(".pdf");
}

function isImageFileName(fileName) {
  return /\.(png|jpe?g|gif|webp|bmp)$/i.test(String(fileName || ""));
}

function forwardAttachmentPreviewUrl(attachment) {
  if (!attachment?.id) return "";
  void ensureAttachmentUrl(attachment.id, { inline: true });
  return getCachedAttachmentUrl(attachment.id, { inline: true });
}

function previewAttachmentPreviewUrl(attachment) {
  if (!attachment?.id) return "";
  void ensureAttachmentUrl(attachment.id, { inline: true });
  return getCachedAttachmentUrl(attachment.id, { inline: true });
}

function attachmentUrlKey(attachmentId, { inline = false } = {}) {
  return `${attachmentId}:${inline ? "inline" : "download"}`;
}

function getCachedAttachmentUrl(attachmentId, options = {}) {
  return attachmentUrls.value[attachmentUrlKey(attachmentId, options)] || "";
}

async function ensureAttachmentUrl(attachmentId, options = {}) {
  if (!attachmentId) return "";

  const key = attachmentUrlKey(attachmentId, options);
  if (attachmentUrls.value[key]) return attachmentUrls.value[key];

  const url = await createAttachmentDownloadUrl(attachmentId, options);
  attachmentUrls.value = { ...attachmentUrls.value, [key]: url };
  return url;
}

async function openAttachmentInNewTab(attachment) {
  if (!attachment?.id) return;

  const win = window.open("", "_blank");
  const url = await ensureAttachmentUrl(attachment.id);
  if (win && url) {
    win.location = url;
  } else if (url) {
    window.open(url, "_blank");
  }
}

function ownerLabel(userId, name, role) {
  return formatUserLabelFromParts({ userId, name, role }, users.value);
}

function sortDocuments(list) {
  return sortInboxDocumentsBy(list, sortBy.value);
}

function applyFilters(list) {
  const qq = q.value.trim().toLowerCase();
  return list.filter((d) => {
    const matchQ =
      !qq ||
      String(d.refNo ?? "").toLowerCase().includes(qq) ||
      String(d.title ?? "").toLowerCase().includes(qq) ||
      String(d.companyName ?? "").toLowerCase().includes(qq) ||
      String(d.toUserName ?? "").toLowerCase().includes(qq);

    const matchStatus = !status.value || d.status === status.value;
    const matchPriority = !priority.value || d.priority === priority.value;
    const matchView = (() => {
      if (inboxMode.value === "received" && viewFilter.value === "unopened") return !isViewedByMe(d);
      if (inboxMode.value === "received" && viewFilter.value === "opened") return isViewedByMe(d);
      if (viewFilter.value === "urgent") return String(d.priority || "").toUpperCase() === "URGENT";
      return true;
    })();

    return matchQ && matchStatus && matchPriority && matchView;
  });
}

function applyNow() {
  const filtered = applyFilters(allRows.value);
  if (inboxMode.value === "received" && sortBy.value === "recent" && !sortTouched.value) {
    rows.value = sortInboxDefaultDisplay(filtered);
    return;
  }
  rows.value = sortDocuments(filtered);
}

watch([q, status, priority, sortBy, viewFilter], () => {
  applyNow();
});

watch(sortBy, (value, previous) => {
  if (previous !== undefined && value !== previous) {
    sortTouched.value = true;
  }
});

watch(
  [forwardTargets, preferredReturnTargetId, canChooseWorkflowTarget],
  ([list, preferredReturnId, canChooseTarget]) => {
    if (!canChooseTarget) {
      forwardToUserId.value = null;
      autoSelectedForwardTargetId.value = null;
      return;
    }

    const resolved = resolveWorkflowAutoTarget({
      candidateTargets: list,
      preferredReturnTargetId: preferredReturnId,
      currentTargetId: forwardToUserId.value,
      autoSelectedTargetId: autoSelectedForwardTargetId.value,
    });

    forwardToUserId.value = resolved.targetId;
    autoSelectedForwardTargetId.value = resolved.autoSelectedTargetId;

    if (resolved.targetId === resolved.autoSelectedTargetId) {
      forwardUserSearch.value = "";
    }
  },
  { immediate: true }
);

watch(
  availableForwardVisibilities,
  (list) => {
    const selected = String(forwardVisibility.value || "").toUpperCase();
    if (!list.includes(selected)) {
      forwardVisibility.value = list[0] || "PUBLIC";
    }
  },
  { immediate: true }
);

async function load() {
  if (inboxMode.value === "sent") {
    await loadSent();
    return;
  }

  await loadReceived();
}

async function loadReceived() {
  loading.value = true;
  error.value = "";
  try {
    const pageSize = 200;
    const maxPages = 20;
    const all = [];
    for (let page = 0; page < maxPages; page += 1) {
      const data = await listMyInboxDocuments({ page, size: pageSize });
      const list = Array.isArray(data) ? data : (data?.content ?? data?.items ?? []);
      all.push(...list);

      if (Array.isArray(data) || list.length < pageSize || page >= ((data?.totalPages ?? 1) - 1)) {
        break;
      }
    }

    allRows.value = all;
    applyNow();
  } catch (e) {
    error.value = e?.message ?? "Failed to load inbox";
    allRows.value = [];
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

async function loadSent() {
  loading.value = true;
  error.value = "";
  try {
    if (!canViewSentMessages.value) {
      allRows.value = [];
      rows.value = [];
      error.value = "You are not allowed to view sent messages.";
      return;
    }

    const pageSize = 300;
    const maxPages = 20;
    const all = [];

    for (let page = 0; page < maxPages; page += 1) {
      const data = await listSentMessages({ page, size: pageSize });
      const list = Array.isArray(data) ? data : (data?.content ?? data?.items ?? []);
      all.push(...list);

      if (Array.isArray(data) || list.length < pageSize || page >= ((data?.totalPages ?? 1) - 1)) {
        break;
      }
    }

    allRows.value = all;
    applyNow();
  } catch (e) {
    error.value = e?.message ?? "Failed to load sent messages";
    allRows.value = [];
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

async function loadUsers() {
  try {
    users.value = await listUsers();
  } catch {
    users.value = [];
  }
}

async function loadWorkflowRules() {
  try {
    const rules = await getWorkflowRules();
    if (Array.isArray(rules?.forwardReturnAllowedStatuses) && rules.forwardReturnAllowedStatuses.length > 0) {
      forwardReturnAllowedStatuses.value = rules.forwardReturnAllowedStatuses.map((statusName) => String(statusName).toUpperCase());
    }
  } catch {
    forwardReturnAllowedStatuses.value = ["PENDING", "IN_PROGRESS", "RETURNED"];
  }
}

function open(id) {
  router.push(`/documents/${id}`);
}

function resolveDocumentId(doc) {
  return doc?.documentId ?? doc?.id;
}

async function doUndoSend(doc) {
  const documentId = resolveDocumentId(doc);
  if (!documentId || forwardBusy.value) return;

  let reason = "";
  if (needsUndoReason(doc)) {
    const entered = window.prompt("Reason for undo send");
    if (entered == null) return;
    reason = entered.trim();
    if (!reason) {
      toast.error("Undo Send requires a reason.");
      return;
    }
  } else if (!window.confirm("Undo this sent document?")) {
    return;
  }

  forwardBusy.value = true;
  try {
    await undoSendDocument(documentId, { reason });
    toast.success("Document send undone successfully.");
    await load();
  } catch (e) {
    toast.error(e?.message || "Failed to undo sent document.");
  } finally {
    forwardBusy.value = false;
  }
}

function rowKey(doc) {
  return doc?.movementId ?? doc?.id;
}

function setMode(mode) {
  if (mode === "sent" && !canViewSentMessages.value) return;
  if (inboxMode.value === mode) return;

  inboxMode.value = mode;
  viewFilter.value = "all";
  sortTouched.value = false;
  error.value = "";
  load();
}

function isViewedByMe(doc) {
  return !!doc?.viewedByMe;
}

function markPreviewDocumentViewed(documentId) {
  if (inboxMode.value !== "received") return;
  allRows.value = markInboxDocumentViewed(allRows.value, documentId);
  applyNow();
}

function canForwardRow(doc) {
  return canForwardInboxDocument({
    doc,
    user: currentUser.value,
    inboxMode: inboxMode.value,
    forwardReturnAllowedStatuses: forwardReturnAllowedStatuses.value,
    availableForwardVisibilities: availableForwardVisibilities.value,
  });
}

function resetPreviewExtras() {
  previewLoadingExtras.value = false;
  previewExtrasError.value = "";
  previewMovements.value = [];
  previewRemarks.value = [];
  previewAttachments.value = [];
  selectedPreviewAttachmentId.value = null;
  attachmentUrls.value = {};
}

async function openPreview(row) {
  const documentId = resolveDocumentId(row);
  if (!documentId) return;

  previewDoc.value = row;
  previewOpen.value = true;
  resetPreviewExtras();

  try {
    const fullDoc = await getDocument(documentId);
    previewDoc.value = { ...row, ...fullDoc, viewedByMe: true };
    markPreviewDocumentViewed(documentId);
  } catch (e) {
    previewExtrasError.value = e?.message || "Could not load full preview details.";
  }

  await loadPreviewExtras(documentId);
}

async function loadPreviewExtras(documentId) {
  previewLoadingExtras.value = true;

  const results = await Promise.allSettled([
    listMovements(documentId),
    listRemarks(documentId),
    listAttachments(documentId),
  ]);

  previewMovements.value = results[0].status === "fulfilled" && Array.isArray(results[0].value) ? results[0].value : [];
  previewRemarks.value = results[1].status === "fulfilled" && Array.isArray(results[1].value) ? results[1].value : [];
  previewAttachments.value = results[2].status === "fulfilled" && Array.isArray(results[2].value) ? results[2].value : [];
  selectedPreviewAttachmentId.value = previewMainAttachment.value?.id ?? previewAttachmentsSorted.value[0]?.id ?? null;

  if (results.some((result) => result.status === "rejected") && !previewExtrasError.value) {
    previewExtrasError.value = "Some preview details are unavailable for your account.";
  }

  previewLoadingExtras.value = false;
}

function closePreview() {
  previewOpen.value = false;
  previewDoc.value = null;
  resetPreviewExtras();
}

function openPreviewDocument() {
  const id = resolveDocumentId(previewDoc.value);
  if (!id) return;
  closePreview();
  open(id);
}

function selectPreviousPreviewAttachment() {
  if (!canGoPreviousPreviewAttachment.value) return;
  selectedPreviewAttachmentId.value = previewAttachmentsSorted.value[selectedPreviewAttachmentIndex.value - 1]?.id ?? null;
}

function selectNextPreviewAttachment() {
  if (!canGoNextPreviewAttachment.value) return;
  selectedPreviewAttachmentId.value = previewAttachmentsSorted.value[selectedPreviewAttachmentIndex.value + 1]?.id ?? null;
}

function resetForwardForm() {
  forwardRemark.value = "";
  forwardVisibility.value = availableForwardVisibilities.value[0] || "PUBLIC";
  forwardToUserId.value = null;
  forwardUserSearch.value = "";
  forwardSearchFocused.value = false;
  forwardMovements.value = [];
  autoSelectedForwardTargetId.value = null;
  forwardAttachments.value = [];
  forwardAttachmentsLoading.value = false;
  forwardAttachmentsError.value = "";
  selectedForwardAttachmentId.value = null;
  attachmentUrls.value = {};
}

async function openForwardDialog(row) {
  const documentId = resolveDocumentId(row);
  if (!documentId) return;

  forwardDoc.value = row;
  forwardOpen.value = true;
  resetForwardForm();

  try {
    const fullDoc = await getDocument(documentId);
    forwardDoc.value = { ...row, ...fullDoc };
  } catch (e) {
    error.value = e?.message || "Could not load document before forwarding.";
  }

  await loadForwardAttachments(documentId);
}

async function loadForwardAttachments(documentId) {
  forwardAttachmentsLoading.value = true;
  forwardAttachmentsError.value = "";
  try {
    const [attachments, movements] = await Promise.all([
      listAttachments(documentId),
      listMovements(documentId),
    ]);
    forwardAttachments.value = Array.isArray(attachments) ? attachments : [];
    forwardMovements.value = Array.isArray(movements) ? movements : [];
    selectedForwardAttachmentId.value = forwardMainAttachment.value?.id ?? forwardAttachmentsSorted.value[0]?.id ?? null;
  } catch (e) {
    forwardAttachments.value = [];
    forwardMovements.value = [];
    selectedForwardAttachmentId.value = null;
    forwardAttachmentsError.value = e?.message || "Could not load file preview.";
  } finally {
    forwardAttachmentsLoading.value = false;
  }
}

function selectPreviousForwardAttachment() {
  if (!canGoPreviousForwardAttachment.value) return;
  selectedForwardAttachmentId.value = forwardAttachmentsSorted.value[selectedForwardAttachmentIndex.value - 1]?.id ?? null;
}

function selectNextForwardAttachment() {
  if (!canGoNextForwardAttachment.value) return;
  selectedForwardAttachmentId.value = forwardAttachmentsSorted.value[selectedForwardAttachmentIndex.value + 1]?.id ?? null;
}

function closeForwardDialog() {
  if (forwardBusy.value) return;
  forwardOpen.value = false;
  forwardDoc.value = null;
  resetForwardForm();
}

function selectForwardUser(user) {
  if (!user) return;
  forwardToUserId.value = Number(user.id);
  forwardUserSearch.value = "";
  forwardSearchFocused.value = false;
  autoSelectedForwardTargetId.value = null;
}

function clearForwardSearch() {
  forwardUserSearch.value = "";
  forwardToUserId.value = null;
  forwardSearchFocused.value = true;
  autoSelectedForwardTargetId.value = null;
}

async function submitForward() {
  error.value = "";

  const documentId = resolveDocumentId(forwardDoc.value);
  if (!documentId) {
    error.value = "Document is missing. Please refresh and try again.";
    return;
  }

  if (!canForwardSelectedDoc.value) {
    error.value = "You are not allowed to forward this document.";
    return;
  }

  if (!forwardToUserId.value) {
    error.value = "Please select a user to forward.";
    return;
  }

  const selectedVisibility = String(forwardVisibility.value || "").toUpperCase();
  if (!availableForwardVisibilities.value.includes(selectedVisibility)) {
    error.value = "You do not have permission for selected forward visibility.";
    return;
  }

  forwardBusy.value = true;
  try {
    await forwardDocument(documentId, {
      toUserId: Number(forwardToUserId.value),
      forwardVisibility: selectedVisibility,
      remarkText: forwardRemark.value.trim() || null,
    });
    forwardOpen.value = false;
    forwardDoc.value = null;
    resetForwardForm();
    toast.success(getWorkflowSenderSuccessMessage("FORWARD"));
    await load();
  } catch (e) {
    error.value = e?.message || "Forward failed.";
  } finally {
    forwardBusy.value = false;
  }
}

async function submitReturn() {
  error.value = "";

  const documentId = resolveDocumentId(forwardDoc.value);
  if (!documentId) {
    error.value = "Document is missing. Please refresh and try again.";
    return;
  }

  if (!canReturnSelectedDoc.value) {
    error.value = "You are not allowed to return this document.";
    return;
  }

  if (!forwardToUserId.value) {
    error.value = "Please select a user to return.";
    return;
  }

  forwardBusy.value = true;
  try {
    await returnDocument(documentId, {
      toUserId: Number(forwardToUserId.value),
      remarkText: forwardRemark.value.trim() || null,
    });
    forwardOpen.value = false;
    forwardDoc.value = null;
    resetForwardForm();
    toast.success(getWorkflowSenderSuccessMessage("RETURN"));
    await load();
  } catch (e) {
    error.value = e?.message || "Return failed.";
  } finally {
    forwardBusy.value = false;
  }
}

function displayDate(doc) {
  if (inboxMode.value === "sent") {
    const source = doc?.sentAt;
    if (!source) return "";
    const parsed = new Date(source);
    if (Number.isNaN(parsed.getTime())) return "";

    const now = new Date();
    const sameDay = parsed.toDateString() === now.toDateString();
    if (sameDay) {
      return parsed.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    }
    return parsed.toLocaleString([], {
      month: "2-digit",
      day: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  }

  const receivedSource = doc?.inboxReceivedAt;
  if (!receivedSource) return "";
  const received = new Date(receivedSource);
  if (Number.isNaN(received.getTime())) return "";

  return received.toLocaleString([], {
    month: "2-digit",
    day: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function displaySentDate(doc) {
  const source = doc?.sentAt;
  if (!source) return "Sent time unavailable";
  const parsed = new Date(source);
  if (Number.isNaN(parsed.getTime())) return "Sent time unavailable";
  return parsed.toLocaleString([], {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function displayMinuteTime(value) {
  if (!value) return "time unknown";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "time unknown";
  return parsed.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function onRealtimeDocumentReceived() {
  if (inboxMode.value !== "received") {
    inboxMode.value = "received";
    viewFilter.value = "all";
  }
  load();
}

function onAuthChanged() {
  authTick.value += 1;
  loadWorkflowRules();
  load();
}

onMounted(() => {
  window.addEventListener("rms_auth_changed", onAuthChanged);
  window.addEventListener("rms_permissions_updated", onAuthChanged);
  window.addEventListener("rms_document_received", onRealtimeDocumentReceived);
  loadUsers();
  loadWorkflowRules();
  load();
});

onUnmounted(() => {
  window.removeEventListener("rms_auth_changed", onAuthChanged);
  window.removeEventListener("rms_permissions_updated", onAuthChanged);
  window.removeEventListener("rms_document_received", onRealtimeDocumentReceived);
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

.modeTabs {
  margin-bottom: 14px;
  display: inline-flex;
  gap: 8px;
  padding: 8px;
}

.modeTab {
  border: 1px solid #dbe3ef;
  background: #f8fafc;
  color: #334155;
  border-radius: 10px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.modeTab:hover {
  background: #eef2f7;
}

.modeTab:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.modeTabActive {
  background: #dbeafe;
  color: #1e3a8a;
  border-color: #bfdbfe;
}

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
  overflow:visible;
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
.tableHintWrap :deep(.hintBubble) {
  left: auto;
  right: 0;
}

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

.mailRow.opened {
  background: #fbfcfe;
}

.mailRow.opened .docTypeBadge,
.mailRow.opened .pill {
  opacity: 0.68;
}

.mailRow.opened .refNo,
.mailRow.opened .titleText {
  color: #475569;
  font-weight: 600;
}

.mailRow.opened .mailPreview,
.mailRow.opened .timeText,
.mailRow.opened .sentMetaLine {
  color: #94a3b8;
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
  color: #0f172a;
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

.sentMetaLine {
  color: #6b7280;
}

.undoSendInfo {
  color: #b45309;
  font-weight: 800;
}

.undoSendBtn {
  border-color: #f59e0b;
  color: #92400e;
  background: #fffbeb;
}

.mailRight {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rowActions {
  display:flex;
  align-items:center;
  gap:6px;
}

.iconAction,
.iconBtn {
  display:inline-flex;
  align-items:center;
  justify-content:center;
  width:36px;
  height:36px;
  border-radius:10px;
  border:1px solid #dbe3ef;
  background:#ffffff;
  color:#334155;
  cursor:pointer;
  transition:background-color 0.16s ease, border-color 0.16s ease, color 0.16s ease, transform 0.12s ease;
}

.iconAction:hover,
.iconBtn:hover {
  background:#eff6ff;
  border-color:#bfdbfe;
  color:#1d4ed8;
}

.iconAction:disabled,
.iconAction.disabled,
.iconBtn:disabled {
  cursor:not-allowed;
  opacity:0.45;
  background:#f8fafc;
  color:#94a3b8;
}

.actionIcon {
  width:16px;
  height:16px;
  stroke-width:2.2;
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
.btn:disabled { cursor:not-allowed; opacity:0.58; }
.btn-sm { padding:8px 12px; font-size:12px; font-weight:700; }
.btn-primary { background:#2563eb; border-color:#2563eb; color:#fff; }
.btn-primary:hover { background:#1d4ed8; border-color:#1d4ed8; }

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

.overlay {
  position:fixed;
  inset:0;
  z-index:3000;
  display:flex;
  align-items:center;
  justify-content:center;
  padding:16px;
  background:rgba(17, 24, 39, 0.56);
  backdrop-filter:blur(2px);
}

.modal {
  width:min(760px, 100%);
  max-height:calc(100vh - 32px);
  overflow:auto;
  border-radius:18px;
  background:#ffffff;
  border:1px solid #e5e7eb;
  box-shadow:0 28px 80px rgba(15, 23, 42, 0.28);
}

.forwardModal {
  width:min(900px, 100%);
}

.fullPreviewModal {
  width:calc(100vw - 28px);
  height:calc(100vh - 28px);
  max-height:calc(100vh - 28px);
  display:flex;
  flex-direction:column;
  border-radius:16px;
}

.modalHead {
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:16px;
  padding:18px 20px;
  border-bottom:1px solid #eef2f7;
  background:
    radial-gradient(80% 110% at 100% 0%, rgba(37, 99, 235, 0.1), transparent 55%),
    linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.modalEyebrow {
  margin-bottom:3px;
  color:#2563eb;
  font-size:11px;
  font-weight:900;
  letter-spacing:0.08em;
  text-transform:uppercase;
}

.modalTitle {
  color:#0f172a;
  font-size:19px;
  font-weight:900;
  letter-spacing:-0.02em;
}

.modalSub {
  color:#64748b;
  font-size:13px;
  margin-top:4px;
}

.modalClose {
  flex:0 0 auto;
  font-size:18px;
}

.previewHeaderActions {
  display:flex;
  align-items:center;
  gap:10px;
  flex-wrap:wrap;
  justify-content:flex-end;
}

.modalBody {
  padding:20px;
}

.fullPreviewBody {
  flex:1;
  min-height:0;
  display:grid;
  grid-template-columns:minmax(0, 1fr) 340px;
  gap:14px;
  padding:14px;
  background:#f8fafc;
}

.fullPreviewViewer {
  min-width:0;
  min-height:0;
  position:relative;
  overflow:hidden;
  border:1px solid #dbe3ef;
  border-radius:16px;
  background:#0f172a;
}

.fullPreviewToolbar {
  display:grid;
  grid-template-columns:34px minmax(0, 360px) 34px;
  gap:8px;
  width:min(520px, 45vw);
  padding:7px;
  border:1px solid rgba(191, 219, 254, 0.88);
  border-radius:13px;
  background:rgba(248, 251, 255, 0.94);
  box-shadow:0 12px 28px rgba(15, 23, 42, 0.18);
  backdrop-filter:blur(4px);
}

.fullPreviewSelect {
  min-width:0;
  height:34px;
  border:1px solid #dbe3ef;
  border-radius:10px;
  background:#ffffff;
  color:#334155;
  font-size:12px;
  font-weight:800;
  padding:0 10px;
}

.fullPreviewFrame,
.fullPreviewImage,
.fullPreviewEmpty {
  width:100%;
  height:100%;
  min-height:0;
}

.fullPreviewFrame {
  border:0;
  background:#ffffff;
}

.fullPreviewImage {
  display:block;
  object-fit:contain;
  background:#111827;
}

.fullPreviewEmpty {
  display:flex;
  flex-direction:column;
  align-items:center;
  justify-content:center;
  gap:10px;
  padding:24px;
  box-sizing:border-box;
  text-align:center;
  color:#cbd5e1;
}

.fullPreviewEmpty b {
  color:#ffffff;
  font-size:18px;
}

.fullPreviewSide {
  min-width:0;
  overflow:auto;
  border:1px solid #e2e8f0;
  border-radius:16px;
  background:#ffffff;
  padding:14px;
}

.modalFoot {
  display:flex;
  justify-content:flex-end;
  gap:10px;
  padding:16px 20px;
  border-top:1px solid #eef2f7;
  background:#f9fafb;
}

.previewPills {
  display:flex;
  align-items:center;
  gap:8px;
  flex-wrap:wrap;
  margin-bottom:16px;
}

.previewGrid {
  display:grid;
  grid-template-columns:1fr 1fr;
  gap:0;
  overflow:hidden;
  border:1px solid #e5e7eb;
  border-radius:14px;
  background:#fff;
}

.previewGrid > div {
  padding:12px 14px;
  border-right:1px solid #eef2f7;
  border-bottom:1px solid #eef2f7;
  color:#111827;
  font-size:13px;
}

.previewGrid > div:nth-child(2n) {
  border-right:0;
}

.previewGrid > div:nth-last-child(-n + 2) {
  border-bottom:0;
}

.label {
  display:block;
  color:#64748b;
  font-size:11px;
  font-weight:900;
  letter-spacing:0.06em;
  text-transform:uppercase;
  margin-bottom:5px;
}

.note {
  margin-top:14px;
  padding:12px 14px;
  border-radius:12px;
  border:1px solid #e5e7eb;
  background:#f8fafc;
  color:#64748b;
  font-size:13px;
}

.noteWarn {
  background:#fff7ed;
  border-color:#fed7aa;
  color:#9a3412;
}

.opsCard {
  margin-top:14px;
  border:1px solid #dbeafe;
  border-radius:14px;
  background:#f8fbff;
  padding:14px;
}

.opsTitle {
  color:#1d4ed8;
  font-size:12px;
  font-weight:900;
  letter-spacing:0.06em;
  text-transform:uppercase;
  margin-bottom:6px;
}

.opsRow {
  display:grid;
  grid-template-columns:130px 1fr;
  align-items:start;
  padding:7px 0;
  border-bottom:1px solid #e5edf8;
  color:#111827;
  font-size:13px;
}

.opsRow:last-child {
  border-bottom:0;
}

.formRow {
  margin-top:14px;
}

.forwardDocPreview {
  display:grid;
  grid-template-columns:250px 1fr;
  gap:16px;
  align-items:stretch;
  margin-bottom:16px;
  padding:12px;
  border:1px solid #dbeafe;
  border-radius:18px;
  background:
    radial-gradient(80% 120% at 0% 0%, rgba(37, 99, 235, 0.08), transparent 58%),
    #f8fbff;
}

.forwardFileBox {
  position:relative;
  min-height:260px;
  overflow:hidden;
  border:1px solid #e2e8f0;
  border-radius:16px;
  background:#ffffff;
  box-shadow:inset 0 0 0 1px rgba(255, 255, 255, 0.75);
}

.forwardFileSwitcher {
  position:absolute;
  z-index:2;
  left:8px;
  right:8px;
  bottom:8px;
  display:grid;
  grid-template-columns:30px 1fr 30px;
  gap:6px;
  padding:6px;
  border:1px solid rgba(191, 219, 254, 0.9);
  border-radius:12px;
  background:rgba(248, 251, 255, 0.94);
  box-shadow:0 10px 24px rgba(15, 23, 42, 0.12);
  backdrop-filter:blur(4px);
}

.forwardAttachmentSelect {
  min-width:0;
  height:30px;
  border:1px solid #dbe3ef;
  border-radius:9px;
  background:#ffffff;
  color:#334155;
  font-size:12px;
  font-weight:700;
  padding:0 8px;
}

.miniSwitchBtn {
  width:30px;
  height:30px;
  border:1px solid #dbe3ef;
  border-radius:9px;
  background:#ffffff;
  color:#1d4ed8;
  cursor:pointer;
  font-size:20px;
  line-height:1;
}

.miniSwitchBtn:disabled {
  cursor:not-allowed;
  opacity:0.45;
  color:#94a3b8;
}

.forwardMiniFrame,
.forwardMiniImage {
  display:block;
  width:100%;
  height:260px;
  border:0;
  background:#f8fafc;
}

.forwardMiniImage {
  object-fit:contain;
  padding:8px;
  box-sizing:border-box;
}

.forwardFileEmpty {
  height:260px;
  display:flex;
  flex-direction:column;
  align-items:center;
  justify-content:center;
  gap:8px;
  padding:18px;
  text-align:center;
  color:#64748b;
  font-size:12px;
}

.forwardFileSwitcher + .forwardFileEmpty {
  padding-bottom:58px;
}

.forwardFileEmpty b {
  color:#0f172a;
  font-size:15px;
}

.forwardDocSummary {
  min-width:0;
  display:flex;
  flex-direction:column;
  gap:12px;
}

.summaryTop {
  display:flex;
  justify-content:space-between;
  gap:12px;
  align-items:flex-start;
}

.summaryRef {
  color:#1d4ed8;
  font-size:12px;
  font-weight:900;
  letter-spacing:0.06em;
  text-transform:uppercase;
}

.summaryTitle {
  margin-top:3px;
  color:#0f172a;
  font-size:18px;
  font-weight:900;
  line-height:1.2;
}

.summaryPills {
  display:flex;
  gap:6px;
  flex-wrap:wrap;
  justify-content:flex-end;
}

.summaryGrid {
  display:grid;
  grid-template-columns:1fr 1fr;
  gap:0;
  overflow:hidden;
  border:1px solid #e5e7eb;
  border-radius:14px;
  background:#fff;
}

.summaryGrid > div {
  min-width:0;
  padding:11px 12px;
  border-right:1px solid #eef2f7;
  border-bottom:1px solid #eef2f7;
  color:#111827;
  font-size:13px;
  overflow:hidden;
  text-overflow:ellipsis;
}

.summaryGrid > div:nth-child(2n) {
  border-right:0;
}

.summaryGrid > div:nth-last-child(-n + 2) {
  border-bottom:0;
}

.formRow .input {
  width:100%;
  box-sizing:border-box;
}

.textarea {
  width:100%;
  box-sizing:border-box;
  min-height:94px;
  resize:vertical;
  border-radius:12px;
  border:1px solid #e5e7eb;
  padding:12px;
  font:inherit;
  outline:none;
  background:#fff;
}

.textarea:focus {
  border-color:#9ca3af;
  box-shadow:0 0 0 3px rgba(229, 231, 235, 0.9);
}

.forwardSearchWrap {
  position:relative;
}

.forwardSearchDropdown {
  position:absolute;
  top:calc(100% + 6px);
  left:0;
  right:0;
  z-index:10;
  max-height:240px;
  overflow:auto;
  border:1px solid #dbe3ef;
  border-radius:14px;
  background:#fff;
  box-shadow:0 18px 40px rgba(15, 23, 42, 0.16);
}

.forwardSearchOption {
  width:100%;
  display:flex;
  flex-direction:column;
  align-items:flex-start;
  gap:3px;
  border:0;
  border-bottom:1px solid #eef2f7;
  background:#fff;
  padding:10px 12px;
  text-align:left;
  cursor:pointer;
}

.forwardSearchOption:hover,
.forwardSearchOption.active {
  background:#eff6ff;
}

.forwardUserName {
  color:#0f172a;
  font-size:13px;
  font-weight:800;
}

.forwardUserMeta,
.forwardSearchMeta,
.forwardSelected span {
  color:#64748b;
  font-size:12px;
}

.forwardSearchEmpty {
  padding:12px;
  color:#64748b;
  font-size:13px;
}

.forwardSelected {
  margin-top:8px;
  display:flex;
  flex-direction:column;
  gap:3px;
  padding:10px 12px;
  border:1px solid #dbeafe;
  border-radius:12px;
  background:#f8fbff;
  color:#0f172a;
  font-size:13px;
}

.forwardSelected.muted {
  border-color:#e5e7eb;
  background:#f8fafc;
  color:#64748b;
}

.forwardSearchMeta {
  margin-top:8px;
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:10px;
}

.linkBtn {
  border:0;
  background:transparent;
  color:#2563eb;
  font-weight:800;
  cursor:pointer;
  padding:0;
}

.linkBtn:disabled {
  cursor:not-allowed;
  opacity:0.55;
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

  .previewGrid,
  .forwardDocPreview,
  .fullPreviewBody,
  .summaryGrid {
    grid-template-columns:1fr;
  }

  .fullPreviewBody {
    overflow:auto;
  }

  .fullPreviewViewer {
    min-height:65vh;
  }

  .fullPreviewSide {
    overflow:visible;
  }

  .previewGrid > div,
  .previewGrid > div:nth-child(2n),
  .previewGrid > div:nth-last-child(-n + 2) {
    border-right:0;
    border-bottom:1px solid #eef2f7;
  }

  .previewGrid > div:last-child {
    border-bottom:0;
  }

  .summaryGrid > div,
  .summaryGrid > div:nth-child(2n),
  .summaryGrid > div:nth-last-child(-n + 2) {
    border-right:0;
    border-bottom:1px solid #eef2f7;
  }

  .summaryGrid > div:last-child {
    border-bottom:0;
  }

  .forwardFileBox,
  .forwardMiniFrame,
  .forwardMiniImage,
  .forwardFileEmpty {
    min-height:220px;
    height:220px;
  }

  .opsRow {
    grid-template-columns:1fr;
    gap:2px;
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

  .rowActions {
    flex-wrap:wrap;
    justify-content:flex-end;
  }

  .modalHead,
  .modalFoot,
  .modalBody {
    padding:14px;
  }

  .fullPreviewModal {
    width:100vw;
    height:100vh;
    max-height:100vh;
    border-radius:0;
  }

  .fullPreviewBody {
    padding:10px;
  }

  .fullPreviewToolbar {
    grid-template-columns:30px minmax(0, 1fr) 30px;
    width:100%;
  }

  .previewHeaderActions {
    align-items:stretch;
    flex-direction:column;
    gap:8px;
  }

  .modalFoot {
    flex-direction:column;
  }

  .modalFoot .btn {
    width:100%;
  }

  .summaryTop {
    flex-direction:column;
  }

  .summaryPills {
    justify-content:flex-start;
  }
}
</style>
