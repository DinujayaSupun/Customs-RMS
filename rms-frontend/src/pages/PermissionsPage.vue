<template>
  <AppLayout>
    <div class="permissionsPage">
      <div class="pageGlow pageGlowA"></div>
      <div class="pageGlow pageGlowB"></div>

      <div class="pageHead">
        <div>
          <h2>Permissions</h2>
          <p class="pageSub">Tune role access and DC escalation controls without code changes.</p>
        </div>
        <div class="headActions">
          <button class="btn" :disabled="loading || saving" @click="refresh">Refresh</button>
          <button class="btn btn-primary" :disabled="loading || saving || (!dirty && !configDirty)" @click="save">
            {{ saving ? "Saving..." : "Save Changes" }}
          </button>
        </div>
      </div>

      <div v-if="!isAdmin" class="errorBox">Only ADMIN can access permission management.</div>

      <div v-else class="card">
        <div v-if="error" class="errorBox">{{ error }}</div>
        <div v-if="success" class="successBox">{{ success }}</div>

        <div class="section">
          <div class="sectionHead">
            <h3>DC Auto Forward</h3>
            <p>If a document is forwarded to DC and DC does not open it in time, auto-forward it to the configured DDC/SDDC user.</p>
          </div>

          <div class="configGrid">
            <label class="toggleWrap configToggle">
              <input type="checkbox" :checked="dcAutoForwardEnabled" @change="onEnabledChange($event.target.checked)" />
              <span>Enabled</span>
            </label>

            <div class="controlBlock">
              <label>Timeout (minutes)</label>
              <input
                type="number"
                class="input"
                min="1"
                max="10080"
                :disabled="!dcAutoForwardEnabled"
                v-model.number="dcTimeoutMinutes"
                @input="markConfigDirty"
              />
            </div>

            <div class="controlBlock">
              <label>Auto-forward receiver (DDC/SDDC)</label>
              <select class="input" :disabled="!dcAutoForwardEnabled" v-model="dcReceiverUserId" @change="markConfigDirty">
                <option :value="null">-- Select receiver --</option>
                <option v-for="u in eligibleReceivers" :key="u.id" :value="u.id">
                  {{ u.fullName }} ({{ u.role }})
                </option>
              </select>
            </div>
          </div>
        </div>

        <div class="section">
          <div class="sectionHead">
            <h3>Workflow Forward/Return Rules</h3>
            <p>Choose which document statuses allow Forward and Return. This controls both the buttons and the backend workflow guard.</p>
          </div>

          <div class="statusRuleGrid">
            <label v-for="statusName in workflowStatuses" :key="statusName" class="toggleWrap statusToggle">
              <input
                type="checkbox"
                :checked="isForwardReturnStatusAllowed(statusName)"
                @change="setForwardReturnStatus(statusName, $event.target.checked)"
              />
              <span>{{ displayStatus(statusName) }}</span>
            </label>
          </div>
        </div>

        <div class="section">
          <div class="sectionHead">
            <h3>Workflow Decision Buttons</h3>
            <p>Control whether document details should show Approve and Reject. When disabled, Done can complete documents without a prior approval step and Reopen can reopen Done documents.</p>
          </div>

          <label class="toggleWrap statusToggle decisionToggle">
            <input
              type="checkbox"
              :checked="approveRejectButtonsEnabled"
              @change="onApproveRejectButtonsEnabledChange($event.target.checked)"
            />
            <span>Enable Approve / Reject Buttons</span>
          </label>
        </div>

        <div class="section">
          <div class="sectionHead">
            <h3>Undo Send</h3>
            <p>Control whether senders can pull back their latest Forward/Return within a time window. You can also require that the receiver has not opened it yet.</p>
          </div>

          <div class="configGrid undoConfigGrid">
            <label class="toggleWrap configToggle">
              <input type="checkbox" :checked="undoSendEnabled" @change="onUndoSendEnabledChange($event.target.checked)" />
              <span>Enable Undo Send</span>
            </label>

            <div class="controlBlock">
              <label>Undo window (hours)</label>
              <input class="input" type="number" min="1" max="168" :disabled="!undoSendEnabled" v-model.number="undoSendWindowHours" @input="markConfigDirty" />
            </div>

            <label class="toggleWrap statusToggle">
              <input type="checkbox" :checked="undoSendRequiresUnopened" :disabled="!undoSendEnabled" @change="onUndoToggle('requiresUnopened', $event.target.checked)" />
              <span>Require receiver unopened</span>
            </label>

            <label class="toggleWrap statusToggle">
              <input type="checkbox" :checked="undoSendRequiresReason" :disabled="!undoSendEnabled" @change="onUndoToggle('requiresReason', $event.target.checked)" />
              <span>Require undo reason</span>
            </label>

            <label class="toggleWrap statusToggle">
              <input type="checkbox" :checked="undoSendNotifyReceiver" :disabled="!undoSendEnabled" @change="onUndoToggle('notifyReceiver', $event.target.checked)" />
              <span>Notify receiver on undo</span>
            </label>

            <label class="toggleWrap statusToggle">
              <input type="checkbox" :checked="undoSendShowExpiredInfo" :disabled="!undoSendEnabled" @change="onUndoToggle('showExpiredInfo', $event.target.checked)" />
              <span>Show expired undo info</span>
            </label>
          </div>

          <div class="statusRuleGrid undoActionsGrid">
            <label v-for="actionName in undoSendActionOptions" :key="actionName" class="toggleWrap statusToggle">
              <input
                type="checkbox"
                :checked="isUndoSendActionAllowed(actionName)"
                :disabled="!undoSendEnabled"
                @change="setUndoSendAction(actionName, $event.target.checked)"
              />
              <span>{{ actionName === 'FORWARD' ? 'Forward' : 'Return' }}</span>
            </label>
          </div>
        </div>

        <div v-if="!loading && permissions.length > 0" class="matrixToolbar">
          <input
            type="search"
            class="input matrixSearch"
            v-model="filterText"
            placeholder="Filter permissions by name or description..."
            aria-label="Filter permissions"
          />
          <span v-if="filterText.trim() && permissionGroups.length === 0" class="muted">No permissions match your filter.</span>
        </div>

        <div v-if="loading || permissions.length === 0" class="tableWrap">
          <table class="table">
            <thead>
              <tr>
                <th scope="col">Permission</th>
                <th v-for="roleName in roles" :key="`head-${roleName}`" scope="col">{{ roleName }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td :colspan="roles.length + 1" class="muted">Loading permissions...</td>
              </tr>
              <tr v-else-if="permissions.length === 0">
                <td :colspan="roles.length + 1" class="muted">No permissions found.</td>
              </tr>
              <tr v-else v-for="permission in permissions" :key="permission">
                <td :title="permission">
                  <div class="permTitle">{{ friendlyLabel(permission) }}</div>
                  <div v-if="permDescription(permission)" class="permDesc">{{ permDescription(permission) }}</div>
                  <div class="permCode truncateText">{{ permission }}</div>
                </td>
                <td v-for="roleName in roles" :key="`${permission}-${roleName}`" class="checkCell">
                  <label class="toggleWrap">
                    <input
                      type="checkbox"
                      :checked="isEnabled(roleName, permission)"
                      @change="setEnabled(roleName, permission, $event.target.checked)"
                      :aria-label="`${friendlyLabel(permission)} for ${roleName}`"
                    />
                    <span>{{ isEnabled(roleName, permission) ? "Yes" : "No" }}</span>
                  </label>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-else class="permissionGroupList">
          <div v-for="group in permissionGroups" :key="group.key" class="permissionGroup">
            <div class="permissionGroupHead">
              <div>
                <h3>{{ group.title }}</h3>
                <p>{{ group.description }}</p>
              </div>
            </div>

            <div class="tableWrap">
              <table class="table">
                <thead>
                  <tr>
                    <th scope="col">Permission</th>
                    <th v-for="roleName in roles" :key="`${group.key}-head-${roleName}`" scope="col" class="checkCell">
                      <div class="roleHead">{{ roleName }}</div>
                      <label class="bulkToggle" :title="`Toggle all shown ${group.title} permissions for ${roleName}`">
                        <input
                          type="checkbox"
                          :checked="isGroupRoleAllEnabled(group, roleName)"
                          @change="setGroupRoleAll(group, roleName, $event.target.checked)"
                          :aria-label="`Enable all ${group.title} permissions for ${roleName}`"
                        />
                        <span>All</span>
                      </label>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="permission in group.permissions" :key="`${group.key}-${permission}`">
                    <td :title="permission">
                      <div class="permTitle">{{ friendlyLabel(permission) }}</div>
                      <div v-if="permDescription(permission)" class="permDesc">{{ permDescription(permission) }}</div>
                      <div class="permCode truncateText">{{ permission }}</div>
                      <label class="bulkToggle permAllToggle" :title="`Toggle ${friendlyLabel(permission)} for all roles`">
                        <input
                          type="checkbox"
                          :checked="isPermissionAllEnabled(permission)"
                          @change="setPermissionForAllRoles(permission, $event.target.checked)"
                          :aria-label="`Enable ${friendlyLabel(permission)} for all roles`"
                        />
                        <span>All roles</span>
                      </label>
                    </td>
                    <td v-for="roleName in roles" :key="`${group.key}-${permission}-${roleName}`" class="checkCell">
                      <label class="toggleWrap">
                        <input
                          type="checkbox"
                          :checked="isEnabled(roleName, permission)"
                          @change="setEnabled(roleName, permission, $event.target.checked)"
                          :aria-label="`${friendlyLabel(permission)} for ${roleName}`"
                        />
                        <span>{{ isEnabled(roleName, permission) ? "Yes" : "No" }}</span>
                      </label>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from "vue";
import { onBeforeRouteLeave } from "vue-router";
import AppLayout from "../layouts/AppLayout.vue";
import {
  adminGetDcAutoForwardConfig,
  adminGetPermissionsMatrix,
  adminSavePermissionsPage,
  adminUpdateDcAutoForwardConfig,
  adminUpdatePermissionsMatrix,
  listUsers,
} from "../api/auth.api";
import { getCurrentUser } from "../auth/currentUser";

const user = ref(getCurrentUser());
const isAdmin = computed(() => user.value?.role === "ADMIN");

const loading = ref(false);
const saving = ref(false);
const error = ref("");
const success = ref("");
const roles = ref([]);
const permissions = ref([]);
const matrix = ref({});
const dirty = ref(false);
const configDirty = ref(false);
const filterText = ref("");
const hasUnsavedChanges = computed(() => dirty.value || configDirty.value);

const allUsers = ref([]);
const dcAutoForwardEnabled = ref(false);
const dcTimeoutMinutes = ref(60);
const dcReceiverUserId = ref(null);
const workflowStatuses = ["PENDING", "IN_PROGRESS", "RETURNED", "APPROVED", "REJECTED", "ISSUED"];
const forwardReturnAllowedStatuses = ref(["PENDING", "IN_PROGRESS", "RETURNED"]);
const approveRejectButtonsEnabled = ref(true);
const undoSendActionOptions = ["FORWARD", "RETURN"];
const undoSendEnabled = ref(true);
const undoSendWindowHours = ref(24);
const undoSendRequiresUnopened = ref(true);
const undoSendAllowedActions = ref(["FORWARD", "RETURN"]);
const undoSendRequiresReason = ref(true);
const undoSendNotifyReceiver = ref(true);
const undoSendShowExpiredInfo = ref(true);

const eligibleReceivers = computed(() =>
  allUsers.value.filter((u) => ["DDC", "SDDC"].includes(String(u.role || "").toUpperCase()))
);

const permissionGroupDefinitions = [
  {
    key: "copied",
    title: "CC / BCC Recipients",
    description: "Control what copied users can see or do when they are not the Report At user.",
    match: (permission) => /^(CC|BCC)_/.test(permission) || [
      "MANAGE_DOCUMENT_RECIPIENTS",
      "MANAGE_ANY_DOCUMENT_RECIPIENTS",
      "VIEW_HIDDEN_RECIPIENTS",
    ].includes(permission),
  },
  {
    key: "workflow",
    title: "Workflow",
    description: "Permissions for routing, decisions, minutes, and document lifecycle actions.",
    match: (permission) => [
      "FORWARD_DOCUMENT",
      "RETURN_DOCUMENT",
      "APPROVE_DOCUMENT",
      "REJECT_DOCUMENT",
      "ISSUE_DOCUMENT",
      "REOPEN_DOCUMENT",
      "ADD_REMARK",
      "VIEW_REMARKS_WHEN_NOT_REPORT_AT",
      "VIEW_ALL_HISTORY",
      "FORWARD_PUBLIC",
      "FORWARD_PRIVATE",
      "CHANGE_DOCUMENT_VISIBILITY",
    ].includes(permission),
  },
  {
    key: "documents",
    title: "Documents And Files",
    description: "Create, update, delete, and attachment permissions for normal document handling.",
    match: (permission) => /DOCUMENT|ATTACHMENT/.test(permission),
  },
  {
    key: "admin",
    title: "Administration",
    description: "System audit log and other system-level access.",
    match: (permission) => /ADMIN|USER|ROLE|LOG|PERMISSION/.test(permission),
  },
];

const permissionGroups = computed(() => {
  const assigned = new Set();
  const groups = [];

  for (const definition of permissionGroupDefinitions) {
    const matched = permissions.value.filter((permission) => {
      const normalized = String(permission || "").toUpperCase();
      return !assigned.has(normalized) && definition.match(normalized);
    });
    matched.forEach((permission) => assigned.add(String(permission || "").toUpperCase()));
    if (matched.length > 0) {
      groups.push({ ...definition, permissions: matched });
    }
  }

  const remaining = permissions.value.filter((permission) => !assigned.has(String(permission || "").toUpperCase()));
  if (remaining.length > 0) {
    groups.push({
      key: "other",
      title: "Other",
      description: "Additional permissions returned by the backend.",
      permissions: remaining,
    });
  }

  // Apply the search filter to what is shown, dropping groups that have no remaining matches.
  const query = filterText.value.trim().toLowerCase();
  if (!query) return groups;
  return groups
    .map((group) => ({ ...group, permissions: group.permissions.filter((permission) => matchesFilter(permission)) }))
    .filter((group) => group.permissions.length > 0);
});

function matchesFilter(permission) {
  const query = filterText.value.trim().toLowerCase();
  if (!query) return true;
  return String(permission).toLowerCase().includes(query)
    || friendlyLabel(permission).toLowerCase().includes(query)
    || permDescription(permission).toLowerCase().includes(query);
}

// Bulk helpers operate on a fresh matrix object so Vue reactivity picks up the change.
function isPermissionAllEnabled(permission) {
  return roles.value.length > 0 && roles.value.every((roleName) => isEnabled(roleName, permission));
}

function setPermissionForAllRoles(permission, enabled) {
  const next = { ...matrix.value };
  for (const roleName of roles.value) next[cellKey(roleName, permission)] = !!enabled;
  matrix.value = next;
  dirty.value = true;
}

function isGroupRoleAllEnabled(group, roleName) {
  return group.permissions.length > 0 && group.permissions.every((permission) => isEnabled(roleName, permission));
}

function setGroupRoleAll(group, roleName, enabled) {
  const next = { ...matrix.value };
  for (const permission of group.permissions) next[cellKey(roleName, permission)] = !!enabled;
  matrix.value = next;
  dirty.value = true;
}

function refresh() {
  if (hasUnsavedChanges.value
      && !window.confirm("Discard unsaved permission changes and reload from the server?")) {
    return;
  }
  load();
}

function cellKey(roleName, permission) {
  return `${roleName}::${permission}`;
}

function buildMatrix(entries) {
  const next = {};
  for (const entry of entries || []) {
    next[cellKey(entry.roleName, entry.permission)] = !!entry.enabled;
  }
  return next;
}

function isEnabled(roleName, permission) {
  return !!matrix.value[cellKey(roleName, permission)];
}

function setEnabled(roleName, permission, enabled) {
  matrix.value = {
    ...matrix.value,
    [cellKey(roleName, permission)]: !!enabled,
  };
  dirty.value = true;
}

function markConfigDirty() {
  configDirty.value = true;
}

function onEnabledChange(enabled) {
  dcAutoForwardEnabled.value = !!enabled;
  markConfigDirty();
}

function onApproveRejectButtonsEnabledChange(enabled) {
  approveRejectButtonsEnabled.value = !!enabled;
  markConfigDirty();
}

function onUndoSendEnabledChange(enabled) {
  undoSendEnabled.value = !!enabled;
  markConfigDirty();
}

function onUndoToggle(key, enabled) {
  const value = !!enabled;
  if (key === "requiresUnopened") undoSendRequiresUnopened.value = value;
  if (key === "requiresReason") undoSendRequiresReason.value = value;
  if (key === "notifyReceiver") undoSendNotifyReceiver.value = value;
  if (key === "showExpiredInfo") undoSendShowExpiredInfo.value = value;
  markConfigDirty();
}

const PERMISSION_LABELS = {
  CREATE_DOCUMENT:                 "Create Document",
  DELETE_DOCUMENT:                 "Delete Own Document",
  DELETE_ANY_DOCUMENT:             "Delete Any Document",
  VIEW_PUBLIC_DOCUMENT:            "View Public Documents",
  VIEW_PRIVATE_DOCUMENT:           "View Private Documents",
  VIEW_OWN_CREATED_DOCUMENTS:      "View Own Created Documents",
  VIEW_ALL_DOCUMENTS:              "View All Documents",
  EDIT_DOCUMENT_DETAILS:           "Edit Document Details",
  ADD_REMARK:                      "Add Minute",
  VIEW_REMARKS_WHEN_NOT_REPORT_AT: "View Minutes When Not Assigned",
  FORWARD_DOCUMENT:                "Forward Document",
  FORWARD_PUBLIC:                  "Forward with Public Visibility",
  FORWARD_PRIVATE:                 "Forward with Private Visibility",
  CHANGE_DOCUMENT_VISIBILITY:      "Change Forward Visibility",
  RETURN_DOCUMENT:                 "Return Document",
  APPROVE_DOCUMENT:                "Approve Document",
  REJECT_DOCUMENT:                 "Reject Document",
  ISSUE_DOCUMENT:                  "Mark as Done",
  REOPEN_DOCUMENT:                 "Reopen Document",
  UPLOAD_ATTACHMENT:               "Upload Attachment",
  DELETE_ATTACHMENT:               "Delete Any Attachment",
  MANAGE_DOCUMENT_RECIPIENTS:      "Manage Recipients",
  MANAGE_ANY_DOCUMENT_RECIPIENTS:  "Manage Any Document's Recipients",
  VIEW_HIDDEN_RECIPIENTS:          "View BCC Recipients",
  CC_VIEW_DOCUMENT:                "CC: View Document",
  CC_VIEW_ATTACHMENTS:             "CC: View Attachments",
  CC_UPLOAD_ATTACHMENTS:           "CC: Upload Attachments",
  CC_DELETE_OWN_ATTACHMENTS:       "CC: Delete Own Attachments",
  CC_VIEW_TIMELINE:                "CC: View Movement Timeline",
  CC_VIEW_MINUTES:                 "CC: View Minutes",
  BCC_VIEW_DOCUMENT:               "BCC: View Document",
  BCC_VIEW_ATTACHMENTS:            "BCC: View Attachments",
  BCC_UPLOAD_ATTACHMENTS:          "BCC: Upload Attachments",
  BCC_DELETE_OWN_ATTACHMENTS:      "BCC: Delete Own Attachments",
  BCC_VIEW_TIMELINE:               "BCC: View Movement Timeline",
  BCC_VIEW_MINUTES:                "BCC: View Minutes",
  VIEW_ALL_HISTORY:                "View Full Movement History",
  VIEW_LOGS:                       "View System Logs",
  VIEW_SENT_MESSAGES:              "View Sent Documents",
};

const PERMISSION_DESCRIPTIONS = {
  CREATE_DOCUMENT:                 "Can register a new document in the system.",
  DELETE_DOCUMENT:                 "Can delete a document that is currently assigned to them in Report At.",
  DELETE_ANY_DOCUMENT:             "Can delete any document in the system regardless of who it is assigned to.",
  VIEW_PUBLIC_DOCUMENT:            "Can open documents that were forwarded with Public visibility.",
  VIEW_PRIVATE_DOCUMENT:           "Can open Private documents, but only if this user was personally involved in the private routing chain (as a sender or recipient). Does not grant blanket access to all private documents.",
  VIEW_OWN_CREATED_DOCUMENTS:      "Can view documents they personally created, even after forwarding them away.",
  VIEW_ALL_DOCUMENTS:              "Can see every document in the system regardless of assignment or visibility.",
  EDIT_DOCUMENT_DETAILS:           "Can update Ref No, Title, Company, Received Date, and Priority while the document is assigned to them.",
  ADD_REMARK:                      "Can write and save minutes on documents currently assigned to them in Report At.",
  VIEW_REMARKS_WHEN_NOT_REPORT_AT: "Can read minutes on a document even when they are not the current Report At user.",
  FORWARD_DOCUMENT:                "Can route a document to another user. Requires Forward Public or Forward Private as well.",
  FORWARD_PUBLIC:                  "Can forward a document with Public visibility so it appears in the recipient's normal inbox.",
  FORWARD_PRIVATE:                 "Can forward a document with Private visibility. The document becomes invisible to everyone except the current owner, users who were personally part of the private routing chain, and the original creator (if they have View Own Created Documents).",
  CHANGE_DOCUMENT_VISIBILITY:      "Can switch whether a forward movement is Public or Private when forwarding.",
  RETURN_DOCUMENT:                 "Can send a document back to the officer who previously forwarded it.",
  APPROVE_DOCUMENT:                "Can mark a document as Approved when it is assigned to them in Report At.",
  REJECT_DOCUMENT:                 "Can mark a document as Rejected when it is assigned to them in Report At.",
  ISSUE_DOCUMENT:                  "Can close a document by marking it Done. Requires prior Approval when Approve/Reject buttons are enabled.",
  REOPEN_DOCUMENT:                 "Can reopen a document that was previously Approved, Rejected, or marked Done.",
  UPLOAD_ATTACHMENT:               "Can upload files to a document while it is assigned to them in Report At.",
  DELETE_ATTACHMENT:               "Can remove any file from a document they have access to.",
  MANAGE_DOCUMENT_RECIPIENTS:      "Can add or remove CC and BCC users on documents currently assigned to them in Report At.",
  MANAGE_ANY_DOCUMENT_RECIPIENTS:  "Can add or remove CC and BCC users on any document in the system regardless of assignment.",
  VIEW_HIDDEN_RECIPIENTS:          "Can see the list of BCC users on a document. BCC users are normally invisible to others.",
  CC_VIEW_DOCUMENT:                "Users added as CC can open and read the document and its details.",
  CC_VIEW_ATTACHMENTS:             "Users added as CC can view and download the document's files.",
  CC_UPLOAD_ATTACHMENTS:           "Users added as CC can upload new files to the document.",
  CC_DELETE_OWN_ATTACHMENTS:       "Users added as CC can delete files they personally uploaded.",
  CC_VIEW_TIMELINE:                "Users added as CC can see the full movement history of the document.",
  CC_VIEW_MINUTES:                 "Users added as CC can read minutes saved on the document.",
  BCC_VIEW_DOCUMENT:               "Users added as BCC can open and read the document. Their presence is hidden from other users.",
  BCC_VIEW_ATTACHMENTS:            "Users added as BCC can view and download the document's files.",
  BCC_UPLOAD_ATTACHMENTS:          "Users added as BCC can upload new files to the document.",
  BCC_DELETE_OWN_ATTACHMENTS:      "Users added as BCC can delete files they personally uploaded.",
  BCC_VIEW_TIMELINE:               "Users added as BCC can see the full movement history of the document.",
  BCC_VIEW_MINUTES:                "Users added as BCC can read minutes saved on the document.",
  VIEW_ALL_HISTORY:                "Can see the complete movement timeline on any document they can open.",
  VIEW_LOGS:                       "Can access the system audit log showing all user activity.",
  VIEW_SENT_MESSAGES:              "Can see the list of documents they have forwarded or returned.",
};

function friendlyLabel(permission) {
  const normalized = String(permission || "").toUpperCase();
  return PERMISSION_LABELS[normalized]
    || String(permission || "").toLowerCase().split("_").map((p) => p.charAt(0).toUpperCase() + p.slice(1)).join(" ");
}

function permDescription(permission) {
  return PERMISSION_DESCRIPTIONS[String(permission || "").toUpperCase()] || "";
}

function displayStatus(statusName) {
  return statusName === "ISSUED" ? "DONE" : friendlyLabel(statusName);
}

function isForwardReturnStatusAllowed(statusName) {
  return forwardReturnAllowedStatuses.value.includes(statusName);
}

function setForwardReturnStatus(statusName, allowed) {
  const next = new Set(forwardReturnAllowedStatuses.value);
  if (allowed) {
    next.add(statusName);
  } else {
    next.delete(statusName);
  }
  forwardReturnAllowedStatuses.value = workflowStatuses.filter((status) => next.has(status));
  markConfigDirty();
}

function isUndoSendActionAllowed(actionName) {
  return undoSendAllowedActions.value.includes(actionName);
}

function setUndoSendAction(actionName, allowed) {
  const next = new Set(undoSendAllowedActions.value);
  if (allowed) {
    next.add(actionName);
  } else {
    next.delete(actionName);
  }
  undoSendAllowedActions.value = undoSendActionOptions.filter((action) => next.has(action));
  markConfigDirty();
}

async function load() {
  loading.value = true;
  error.value = "";
  success.value = "";

  try {
    const [data, config, users] = await Promise.all([
      adminGetPermissionsMatrix(),
      adminGetDcAutoForwardConfig(),
      listUsers(),
    ]);

    roles.value = Array.isArray(data?.roles) ? data.roles : [];
    permissions.value = Array.isArray(data?.permissions) ? data.permissions : [];
    matrix.value = buildMatrix(data?.entries);

    allUsers.value = Array.isArray(users) ? users : [];
    dcAutoForwardEnabled.value = !!config?.enabled;
    dcTimeoutMinutes.value = Number(config?.timeoutMinutes || 60);
    dcReceiverUserId.value = config?.receiverUserId == null ? null : Number(config.receiverUserId);
    forwardReturnAllowedStatuses.value = Array.isArray(config?.forwardReturnAllowedStatuses) && config.forwardReturnAllowedStatuses.length > 0
      ? workflowStatuses.filter((statusName) => config.forwardReturnAllowedStatuses.includes(statusName))
      : ["PENDING", "IN_PROGRESS", "RETURNED"];
    approveRejectButtonsEnabled.value = config?.approveRejectButtonsEnabled !== false;
    undoSendEnabled.value = config?.undoSendEnabled !== false;
    undoSendWindowHours.value = Number(config?.undoSendWindowHours || 24);
    undoSendRequiresUnopened.value = config?.undoSendRequiresUnopened !== false;
    undoSendAllowedActions.value = Array.isArray(config?.undoSendAllowedActions) && config.undoSendAllowedActions.length > 0
      ? undoSendActionOptions.filter((actionName) => config.undoSendAllowedActions.includes(actionName))
      : ["FORWARD", "RETURN"];
    undoSendRequiresReason.value = config?.undoSendRequiresReason !== false;
    undoSendNotifyReceiver.value = config?.undoSendNotifyReceiver !== false;
    undoSendShowExpiredInfo.value = config?.undoSendShowExpiredInfo !== false;

    dirty.value = false;
    configDirty.value = false;
  } catch (e) {
    error.value = e?.message || "Failed to load permissions.";
  } finally {
    loading.value = false;
  }
}

async function save() {
  saving.value = true;
  error.value = "";
  success.value = "";

  try {
    const timeout = Number(dcTimeoutMinutes.value);
    if (!Number.isFinite(timeout) || timeout < 1 || timeout > 10080) {
      throw new Error("Timeout must be between 1 and 10080 minutes.");
    }
    if (dcAutoForwardEnabled.value && !dcReceiverUserId.value) {
      throw new Error("Select a DDC/SDDC receiver when DC auto forward is enabled.");
    }
    if (forwardReturnAllowedStatuses.value.length === 0) {
      throw new Error("Select at least one status where Forward/Return is allowed.");
    }
    const undoWindowHours = Number(undoSendWindowHours.value);
    if (!Number.isFinite(undoWindowHours) || undoWindowHours < 1 || undoWindowHours > 168) {
      throw new Error("Undo Send window must be between 1 and 168 hours.");
    }
    if (undoSendAllowedActions.value.length === 0) {
      throw new Error("Select Forward, Return, or both for Undo Send.");
    }

    const entries = [];
    for (const permission of permissions.value) {
      for (const roleName of roles.value) {
        // Send the full matrix so unchecked permissions are persisted as explicit disabled rows.
        entries.push({
          roleName,
          permission,
          enabled: isEnabled(roleName, permission),
        });
      }
    }

    const savedPage = await adminSavePermissionsPage({
      permissionMatrix: { entries },
      dcAutoForwardConfig: {
        enabled: !!dcAutoForwardEnabled.value,
        timeoutMinutes: timeout,
        receiverUserId: dcReceiverUserId.value == null ? null : Number(dcReceiverUserId.value),
        forwardReturnAllowedStatuses: forwardReturnAllowedStatuses.value,
        approveRejectButtonsEnabled: !!approveRejectButtonsEnabled.value,
        undoSendEnabled: !!undoSendEnabled.value,
        undoSendWindowHours: undoWindowHours,
        undoSendRequiresUnopened: !!undoSendRequiresUnopened.value,
        undoSendAllowedActions: undoSendAllowedActions.value,
        undoSendRequiresReason: !!undoSendRequiresReason.value,
        undoSendNotifyReceiver: !!undoSendNotifyReceiver.value,
        undoSendShowExpiredInfo: !!undoSendShowExpiredInfo.value,
      },
    });

    const data = savedPage?.permissionMatrix;
    const updatedConfig = savedPage?.dcAutoForwardConfig;

    roles.value = Array.isArray(data?.roles) ? data.roles : roles.value;
    permissions.value = Array.isArray(data?.permissions) ? data.permissions : permissions.value;
    matrix.value = buildMatrix(data?.entries);

    dcAutoForwardEnabled.value = !!updatedConfig?.enabled;
    dcTimeoutMinutes.value = Number(updatedConfig?.timeoutMinutes || timeout);
    dcReceiverUserId.value = updatedConfig?.receiverUserId == null ? null : Number(updatedConfig.receiverUserId);
    forwardReturnAllowedStatuses.value = Array.isArray(updatedConfig?.forwardReturnAllowedStatuses) && updatedConfig.forwardReturnAllowedStatuses.length > 0
      ? workflowStatuses.filter((statusName) => updatedConfig.forwardReturnAllowedStatuses.includes(statusName))
      : forwardReturnAllowedStatuses.value;
    approveRejectButtonsEnabled.value = updatedConfig?.approveRejectButtonsEnabled !== false;
    undoSendEnabled.value = updatedConfig?.undoSendEnabled !== false;
    undoSendWindowHours.value = Number(updatedConfig?.undoSendWindowHours || undoWindowHours);
    undoSendRequiresUnopened.value = updatedConfig?.undoSendRequiresUnopened !== false;
    undoSendAllowedActions.value = Array.isArray(updatedConfig?.undoSendAllowedActions) && updatedConfig.undoSendAllowedActions.length > 0
      ? undoSendActionOptions.filter((actionName) => updatedConfig.undoSendAllowedActions.includes(actionName))
      : undoSendAllowedActions.value;
    undoSendRequiresReason.value = updatedConfig?.undoSendRequiresReason !== false;
    undoSendNotifyReceiver.value = updatedConfig?.undoSendNotifyReceiver !== false;
    undoSendShowExpiredInfo.value = updatedConfig?.undoSendShowExpiredInfo !== false;

    dirty.value = false;
    configDirty.value = false;

    success.value = "Permissions updated successfully.";
  } catch (e) {
    error.value = e?.message || "Failed to save permissions.";
  } finally {
    saving.value = false;
  }
}

// Warn before losing unsaved permission edits on tab close/reload and on in-app navigation.
function beforeUnloadHandler(event) {
  if (!hasUnsavedChanges.value) return;
  event.preventDefault();
  event.returnValue = "";
}

onMounted(() => window.addEventListener("beforeunload", beforeUnloadHandler));
onUnmounted(() => window.removeEventListener("beforeunload", beforeUnloadHandler));

onBeforeRouteLeave(() => {
  if (hasUnsavedChanges.value) {
    return window.confirm("You have unsaved permission changes. Leave this page without saving?");
  }
  return true;
});

if (isAdmin.value) {
  load();
}
</script>

<style scoped>
.permissionsPage {
  position: relative;
  padding: 4px 2px 0;
}

.pageGlow {
  position: absolute;
  border-radius: 999px;
  filter: blur(42px);
  z-index: 0;
  pointer-events: none;
  opacity: 0.56;
}

.pageGlowA {
  width: 230px;
  height: 230px;
  right: 5%;
  top: -10px;
  background: radial-gradient(circle at center, rgba(14, 116, 144, 0.24), rgba(14, 116, 144, 0));
}

.pageGlowB {
  width: 180px;
  height: 180px;
  left: 10%;
  top: 44%;
  background: radial-gradient(circle at center, rgba(30, 64, 175, 0.22), rgba(30, 64, 175, 0));
}

.pageHead {
  position: relative;
  z-index: 1;
  display:flex;
  align-items:flex-start;
  justify-content:space-between;
  gap:12px;
  margin-bottom:14px;
}

h2 {
  margin:0;
  color:#0f172a;
  font-size:1.45rem;
  letter-spacing:-0.02em;
}

.pageSub {
  margin:6px 0 0;
  color:#475569;
  font-size:13px;
}

.headActions { display:flex; gap:10px; }

.section {
  margin-bottom: 16px;
  padding: 14px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: linear-gradient(170deg, #f8fbff 0%, #f3f9ff 100%);
}

.sectionHead h3 {
  margin: 0;
  font-size: 16px;
  color: #1e3a8a;
}

.sectionHead p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #6b7280;
}

.permissionGroupList {
  display:grid;
  gap:14px;
}

.permissionGroup {
  border:1px solid #dbeafe;
  border-radius:14px;
  background:#fff;
  overflow:hidden;
}

.permissionGroupHead {
  display:flex;
  justify-content:space-between;
  gap:12px;
  padding:14px;
  border-bottom:1px solid #e5edf8;
  background:#f8fbff;
}

.permissionGroupHead h3 {
  margin:0;
  color:#1e3a8a;
  font-size:15px;
}

.permissionGroupHead p {
  margin:4px 0 0;
  color:#64748b;
  font-size:12px;
}

.configGrid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 180px 1fr 1fr;
  gap: 12px;
  align-items: end;
}

.configToggle {
  height: 40px;
  display: inline-flex;
  align-items: center;
}

.statusRuleGrid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(3, minmax(150px, 1fr));
  gap: 10px;
}

.statusToggle {
  justify-content: space-between;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: #ffffff;
  padding: 10px 12px;
}

.controlBlock {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.controlBlock label {
  font-size: 12px;
  font-weight: 700;
  color: #374151;
}

.input {
  height: 40px;
  border-radius: 10px;
  border: 1px solid #d1d5db;
  padding: 0 10px;
  font-size: 13px;
  background: #fff;
  transition: border-color .2s ease, box-shadow .2s ease;
}

.input:focus {
  border-color:#2563eb;
  box-shadow:0 0 0 3px rgba(37, 99, 235, 0.14);
  outline:none;
}

.card {
  position: relative;
  z-index: 1;
  background:linear-gradient(160deg, #ffffff 0%, #f8fbff 100%);
  border:1px solid #dbe8ff;
  border-radius:14px;
  padding:16px;
  box-shadow:0 12px 30px rgba(15, 23, 42, 0.08);
}

.tableWrap {
  overflow:auto;
  border:1px solid #e2e8f0;
  border-radius:12px;
}
.table { width:100%; border-collapse:collapse; min-width:900px; table-layout:fixed; }
.table th, .table td { border-bottom:1px solid #e5e7eb; padding:12px 10px; text-align:left; vertical-align:top; }
.table th { font-size:12px; text-transform:uppercase; letter-spacing:0.04em; color:#6b7280; background:#f9fafb; }
.table th:first-child,
.table td:first-child {
  width: 340px;
}
.table th:not(:first-child),
.table td.checkCell {
  width: 90px;
}
.truncateText {
  display:block;
  overflow:hidden;
  text-overflow:ellipsis;
  white-space:nowrap;
}

.permTitle { font-weight:700; color:#111827; }
.permDesc { font-size:11px; color:#374151; margin-top:3px; line-height:1.4; }
.permCode { font-size:10px; color:#9ca3af; margin-top:4px; }
.checkCell { text-align:center; }
.toggleWrap { display:inline-flex; align-items:center; gap:8px; font-size:13px; color:#374151; }

.matrixToolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin: 4px 0 12px;
}
.matrixSearch { max-width: 360px; width: 100%; }
.roleHead {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: #6b7280;
}
.bulkToggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  font-size: 10px;
  font-weight: 600;
  text-transform: none;
  letter-spacing: 0;
  color: #2563eb;
  cursor: pointer;
}
.permAllToggle { margin-top: 6px; color: #64748b; }

.btn {
  padding:10px 12px;
  border-radius:10px;
  border:1px solid #d1d5db;
  background:#fff;
  cursor:pointer;
  transition: all .2s ease;
}
.btn-primary { background:#2563eb; border-color:#2563eb; color:#fff; }
.btn:hover:not(:disabled) { background:#f8fafc; }
.btn:disabled { opacity:0.6; cursor:not-allowed; }

.errorBox { background:#fef2f2; border:1px solid #fecaca; color:#991b1b; padding:10px 12px; border-radius:8px; margin-bottom:12px; }
.successBox { background:#ecfdf5; border:1px solid #a7f3d0; color:#065f46; padding:10px 12px; border-radius:8px; margin-bottom:12px; }
.muted { color:#6b7280; text-align:center; }

@media (max-width: 1100px) {
  .configGrid {
    grid-template-columns: 1fr;
    align-items: stretch;
  }
}

@media (max-width: 760px) {
  .pageHead {
    flex-direction: column;
    align-items: stretch;
  }

  .headActions {
    width: 100%;
  }

  .headActions .btn {
    flex: 1;
  }

  .statusRuleGrid {
    grid-template-columns: 1fr;
  }
}
</style>
