<template>
  <AppLayout>
    <div class="usersPage">
      <div class="pageGlow pageGlowA"></div>
      <div class="pageGlow pageGlowB"></div>

      <div class="pageHead">
        <div>
          <h2>User Management</h2>
          <p class="pageSub">Manage staff accounts, access lifecycle, password resets, and duplicate cleanup from one admin workspace.</p>
        </div>
        <div class="headActions">
          <button class="btn" @click="load" :disabled="loading || saving">Refresh</button>
          <button class="btn" @click="toggleCreatePanel">{{ createPanelOpen ? "Hide Create Form" : "Create User" }}</button>
          <button class="btn btn-primary" @click="downloadCsv" :disabled="loading || saving">Export CSV</button>
        </div>
      </div>

      <div v-if="!isAdmin" class="errorBox">Only ADMIN can access user management.</div>

      <template v-else>
        <div class="statsGrid">
          <div class="statCard">
            <span class="statLabel">Visible Users</span>
            <strong class="statValue">{{ summaryRows.length }}</strong>
            <span class="statMeta">All matching filtered results</span>
          </div>
          <div class="statCard">
            <span class="statLabel">Active Users</span>
            <strong class="statValue">{{ activeUsersCount }}</strong>
            <span class="statMeta">Ready to sign in</span>
          </div>
          <div class="statCard">
            <span class="statLabel">Inactive Users</span>
            <strong class="statValue">{{ inactiveUsersCount }}</strong>
            <span class="statMeta">Disabled accounts</span>
          </div>
          <div class="statCard warn">
            <span class="statLabel">Duplicate Groups</span>
            <strong class="statValue">{{ duplicateGroups.length }}</strong>
            <span class="statMeta">Same name and role</span>
          </div>
        </div>

        <div class="card">
          <div v-if="error" class="errorBox">{{ error }}</div>

          <section class="section">
            <div class="sectionHead">
              <div>
                <div class="sectionEyebrow">Find Users</div>
                <h3>Search and Filter</h3>
                <p>Quickly narrow the list by name, role, or account status.</p>
              </div>
              <button class="btn btn-sm" @click="resetFilters" :disabled="loading">Clear Filters</button>
            </div>

            <div class="filters">
              <div class="field wide">
                <label>Search</label>
                <input
                  v-model="search"
                  class="input"
                  placeholder="Search name, username, email, phone, or department"
                  @keydown.enter="applyFilters"
                />
              </div>
              <div class="field">
                <label>Role</label>
                <select v-model="role" class="input">
                  <option value="">All Roles</option>
                  <option v-for="r in roles" :key="r" :value="r">{{ r }}</option>
                </select>
              </div>
              <div class="field">
                <label>Status</label>
                <select v-model="active" class="input">
                  <option value="">All Status</option>
                  <option value="true">Active</option>
                  <option value="false">Inactive</option>
                </select>
              </div>
              <div class="filterActions">
                <button class="btn btn-primary" @click="applyFilters" :disabled="loading">Apply</button>
              </div>
            </div>
          </section>

          <section v-if="createPanelOpen" class="section createSection">
            <div class="sectionHead">
              <div>
                <div class="sectionEyebrow">New Account</div>
                <h3>Create User</h3>
                <p>Add a new officer account with role, contact details, and initial password.</p>
              </div>
            </div>

            <div class="createGrid">
              <div class="field">
                <label>Full Name</label>
                <input v-model="createForm.fullName" class="input" placeholder="Full name" />
              </div>
              <div class="field">
                <label>Username</label>
                <input v-model="createForm.username" class="input" placeholder="Username" />
              </div>
              <div class="field">
                <label>Email</label>
                <input v-model="createForm.email" class="input" placeholder="Email" />
              </div>
              <div class="field">
                <label>Phone</label>
                <input v-model="createForm.phone" class="input" placeholder="Phone" />
              </div>
              <div class="field">
                <label>Department</label>
                <input v-model="createForm.department" class="input" placeholder="Department" />
              </div>
              <div class="field">
                <label>Role</label>
                <select v-model="createForm.role" class="input">
                  <option value="">Select role</option>
                  <option v-for="r in roles" :key="`create-${r}`" :value="r">{{ r }}</option>
                </select>
              </div>
              <div class="field">
                <label>Password</label>
                <div class="passwordField">
                  <input
                    v-model="createForm.password"
                    class="input passwordInput"
                    :type="showCreatePassword ? 'text' : 'password'"
                    placeholder="Temporary password"
                    autocomplete="new-password"
                  />
                  <button
                    type="button"
                    class="passwordToggle"
                    :aria-label="showCreatePassword ? 'Hide temporary password' : 'Show temporary password'"
                    :aria-pressed="showCreatePassword"
                    @click="showCreatePassword = !showCreatePassword"
                  >
                    <EyeOff v-if="showCreatePassword" class="passwordIcon" aria-hidden="true" />
                    <Eye v-else class="passwordIcon" aria-hidden="true" />
                  </button>
                </div>
              </div>
              <div class="createActions">
                <button class="btn" @click="resetCreateForm" :disabled="saving">Clear</button>
                <button class="btn btn-primary" :disabled="saving" @click="createUser">{{ saving ? "Creating..." : "Create User" }}</button>
              </div>
            </div>
          </section>

          <section class="section">
            <div class="sectionHead">
              <div>
                <div class="sectionEyebrow">User Directory</div>
                <h3>Accounts</h3>
                <p>Review each account, contact details, role, and available admin actions.</p>
              </div>
              <div class="tableMeta">Page {{ page + 1 }}</div>
            </div>

            <div class="bulkPanel" :class="bulkMode === 'deactivate' ? 'bulkPanelInfo' : 'bulkPanelDanger'">
              <div class="bulkPanelHead">
                <div>
                  <div class="sectionEyebrow">{{ bulkMode === "deactivate" ? "Bulk Deactivate" : "Bulk Delete" }}</div>
                  <h4>{{ bulkModeTitle }}</h4>
                  <p>{{ bulkModeDescription }}</p>
                </div>
                <div class="bulkModeSwitch">
                  <button
                    class="modeBtn"
                    :class="bulkMode === 'deactivate' ? 'modeBtnActive modeBtnPrimary' : ''"
                    @click="setBulkMode('deactivate')"
                  >
                    Deactivate Users
                  </button>
                  <button
                    class="modeBtn"
                    :class="bulkMode === 'delete' ? 'modeBtnActive modeBtnDanger' : ''"
                    @click="setBulkMode('delete')"
                  >
                    Delete Users
                  </button>
                </div>
              </div>

              <div class="bulkPanelBody">
                <div class="bulkStats">
                  <span class="bulkStat">Matching users: <b>{{ eligibleUsers.length }}</b></span>
                  <span class="bulkStat">Visible on this page: <b>{{ eligibleRows.length }}</b></span>
                  <span class="bulkStat">Selected: <b>{{ selectedUsers.length }}</b></span>
                </div>
                <div class="bulkActionsBar">
                  <button class="btn btn-sm" :disabled="!eligibleUsers.length" @click="selectAllEligibleRows">Select All Matching</button>
                  <button class="btn btn-sm" :disabled="!selectedUsers.length" @click="clearSelectedUsers">Clear</button>
                  <button
                    class="btn"
                    :class="bulkMode === 'deactivate' ? 'btn-primary' : 'btn-danger'"
                    :disabled="saving || !selectedUsers.length"
                    @click="openBulkActionModal"
                  >
                    {{ bulkActionLabel }}
                  </button>
                </div>
              </div>
            </div>

            <div class="tableWrap">
              <table class="table">
                <colgroup>
                  <col class="col-select" />
                  <col class="col-user" />
                  <col class="col-contact" />
                  <col class="col-department" />
                  <col class="col-status" />
                  <col class="col-actions" />
                </colgroup>
                <thead>
                  <tr>
                    <th>
                      <input
                        type="checkbox"
                        :checked="allEligibleRowsSelected"
                        :indeterminate.prop="someEligibleRowsSelected && !allEligibleRowsSelected"
                        @change="toggleSelectAllEligibleRows"
                        :disabled="eligibleUsers.length === 0"
                        :title="bulkMode === 'deactivate' ? 'Select all matching active non-admin users' : 'Select all matching inactive users'"
                      />
                    </th>
                    <th>User</th>
                    <th>Contact</th>
                    <th>Department</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="loading">
                    <td colspan="6" class="muted">Loading users...</td>
                  </tr>
                  <tr v-else-if="rows.length === 0">
                    <td colspan="6" class="muted">No users found for the current filters.</td>
                  </tr>
                  <tr v-else v-for="u in rows" :key="u.id">
                    <td class="selectCell">
                      <input
                        type="checkbox"
                        :checked="isSelected(u.id)"
                        :disabled="!isSelectableUser(u)"
                        @change="toggleSelectedUser(u.id)"
                      />
                    </td>
                    <td>
                      <div class="userCell">
                        <div class="userAvatar">{{ userInitials(u) }}</div>
                        <div class="userInfo">
                          <div class="userName">{{ formatAdminUserLabel(u) }}</div>
                          <div class="userMeta">{{ u.username }}<span v-if="u.department"> • {{ u.department }}</span></div>
                        </div>
                      </div>
                    </td>
                    <td>
                      <div class="contactLine" :title="u.email || '-'">{{ u.email || "-" }}</div>
                      <div class="contactSub" :title="u.phone || '-'">{{ u.phone || "-" }}</div>
                    </td>
                    <td :title="u.department || '-'">
                      <span class="truncateText">{{ u.department || "-" }}</span>
                    </td>
                    <td>
                      <span class="pill" :class="u.active ? 'pill-active' : 'pill-inactive'">{{ u.active ? "ACTIVE" : "INACTIVE" }}</span>
                    </td>
                    <td>
                      <div class="actions">
                        <button class="btn btn-sm" @click="openEditModal(u)">Edit</button>
                        <button class="btn btn-sm" @click="openResetPasswordModal(u)">Reset Password</button>
                        <button class="btn btn-sm" @click="openPermissionsModal(u)">Permissions</button>
                        <button
                          v-if="u.active"
                          class="btn btn-sm danger"
                          @click="deactivateUser(u)"
                        >
                          Deactivate
                        </button>
                        <button v-else class="btn btn-sm" @click="activateUser(u)">Activate</button>
                        <button v-if="!u.active" class="btn btn-sm danger ghostDanger" @click="openDeleteModal(u)">Delete</button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div class="pager">
              <button class="btn btn-sm" :disabled="page === 0 || loading" @click="goToPrevPage">Prev</button>
              <span>Page {{ page + 1 }}</span>
              <button class="btn btn-sm" :disabled="last || loading" @click="goToNextPage">Next</button>
            </div>
          </section>

          <section class="section dupSection">
            <div class="sectionHead">
              <div>
                <div class="sectionEyebrow">Cleanup</div>
                <h3>Duplicate Users</h3>
                <p>Review possible duplicates and merge inactive or duplicate records into the active target user.</p>
              </div>
            </div>

            <div v-if="duplicateGroups.length === 0" class="emptyPanel">No duplicate candidates found.</div>

            <div v-else class="dupGrid">
              <div v-for="group in duplicateGroups" :key="`${group.fullName}-${group.role}`" class="dupGroup">
                <div class="dupHead">{{ group.fullName }} • {{ group.role }}</div>
                <div class="dupRows">
                  <div v-for="u in group.users" :key="`dup-${u.id}`" class="dupRow">
                    <div>
                      <div class="dupName" :title="formatAdminUserLabel(u)">{{ formatAdminUserLabel(u) }}</div>
                      <div class="dupMeta">{{ u.username }} • {{ u.active ? "ACTIVE" : "INACTIVE" }}</div>
                    </div>
                    <button
                      class="btn btn-sm"
                      :disabled="!u.active || !mergeTargetId(group.users, u.id)"
                      @click="mergeDuplicate(u.id, mergeTargetId(group.users, u.id))"
                    >
                      Merge Into {{ mergeTargetLabel(group.users, u.id) }}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </section>
        </div>
      </template>
    </div>

    <div v-if="editModalOpen" class="overlay">
      <div class="modal">
        <div class="modalHead">
          <div>
            <div class="modalTitle">Edit User</div>
            <div class="modalSub">{{ editingUserLabel }}</div>
          </div>
          <button class="btn btn-sm" @click="closeEditModal">Close</button>
        </div>

        <div class="modalBody formStack">
          <div class="field">
            <label>Full Name</label>
            <input v-model="editForm.fullName" class="input" />
          </div>
          <div class="field">
            <label>Username</label>
            <input v-model="editForm.username" class="input" />
          </div>
          <div class="field">
            <label>Email</label>
            <input v-model="editForm.email" class="input" />
          </div>
          <div class="field">
            <label>Phone</label>
            <input v-model="editForm.phone" class="input" />
          </div>
          <div class="field">
            <label>Department</label>
            <input v-model="editForm.department" class="input" />
          </div>
        </div>

        <div class="modalFoot">
          <button class="btn" @click="closeEditModal">Cancel</button>
          <button class="btn btn-primary" :disabled="saving" @click="saveEditUser">{{ saving ? "Saving..." : "Save Changes" }}</button>
        </div>
      </div>
    </div>

    <div v-if="resetPasswordModalOpen" class="overlay">
      <div class="modal">
        <div class="modalHead">
          <div>
            <div class="modalTitle">Reset Password</div>
            <div class="modalSub">{{ resetPasswordUserLabel }}</div>
          </div>
          <button class="btn btn-sm" @click="closeResetPasswordModal">Close</button>
        </div>

        <div class="modalBody formStack">
          <div class="field">
            <label>New Password</label>
            <div class="passwordField">
              <input
                v-model="resetPasswordValue"
                class="input passwordInput"
                :type="showResetPassword ? 'text' : 'password'"
                placeholder="Enter new password"
                autocomplete="new-password"
              />
              <button
                type="button"
                class="passwordToggle"
                :aria-label="showResetPassword ? 'Hide new password' : 'Show new password'"
                :aria-pressed="showResetPassword"
                @click="showResetPassword = !showResetPassword"
              >
                <EyeOff v-if="showResetPassword" class="passwordIcon" aria-hidden="true" />
                <Eye v-else class="passwordIcon" aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>

        <div class="modalFoot">
          <button class="btn" @click="closeResetPasswordModal">Cancel</button>
          <button class="btn btn-primary" :disabled="saving || !resetPasswordValue.trim()" @click="confirmResetPassword">
            {{ saving ? "Resetting..." : "Reset Password" }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="permissionsModalOpen" class="overlay">
      <div class="modal">
        <div class="modalHead">
          <div>
            <div class="modalTitle">Permissions</div>
            <div class="modalSub">{{ permissionsUserLabel }}</div>
          </div>
          <button class="btn btn-sm" @click="closePermissionsModal">Close</button>
        </div>

        <div class="modalBody">
          <p class="permsHint">
            Each permission inherits from the role by default. Override it for this user only:
            <b>Grant</b> adds it, <b>Revoke</b> removes it, regardless of the role.
          </p>
          <div v-if="permissionsLoading" class="permsLoading">Loading…</div>
          <table v-else class="permsTable">
            <thead>
              <tr><th>Permission</th><th>Role default</th><th>Override</th></tr>
            </thead>
            <tbody>
              <tr v-for="entry in permissionsEntries" :key="entry.permission">
                <td class="permName">{{ entry.permission }}</td>
                <td>
                  <span :class="entry.roleDefault ? 'permYes' : 'permNo'">
                    {{ entry.roleDefault ? "Allowed" : "Denied" }}
                  </span>
                </td>
                <td>
                  <select v-model="entry.mode" class="input permSelect">
                    <option value="INHERIT">Inherit ({{ entry.roleDefault ? "Allowed" : "Denied" }})</option>
                    <option value="GRANT">Grant</option>
                    <option value="REVOKE">Revoke</option>
                  </select>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="modalFoot">
          <button class="btn" @click="closePermissionsModal">Cancel</button>
          <button class="btn btn-primary" :disabled="saving || permissionsLoading" @click="savePermissions">
            {{ saving ? "Saving..." : "Save Permissions" }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="deactivateModalOpen" class="overlay">
      <div class="modal">
        <div class="modalHead">
          <div>
            <div class="modalTitle">Deactivate User</div>
          <div class="modalSub">Select the fallback DC who should receive Report At assignment for active documents.</div>
          </div>
          <button class="btn btn-sm" @click="closeDeactivateModal">Close</button>
        </div>

        <div class="modalBody formStack">
          <div v-if="deactivateModalError" class="modalAlert modalAlertDanger">{{ deactivateModalError }}</div>
          <div class="noticeLine">
            Deactivating: <b>{{ deactivateTargetUserLabel }}</b>
          </div>
          <div v-if="deactivateTargetUser?.role === 'ADMIN'" class="adminWarning">
            This is an ADMIN account. You cannot deactivate your own admin account, and the system must always keep at least one active admin.
          </div>
          <div class="field">
            <label>Fallback DC</label>
            <select v-model.number="deactivateFallbackDcUserId" class="input">
              <option
                v-for="dc in availableFallbackDcs(deactivateTargetUser?.id)"
                :key="`fallback-${dc.id}`"
                :value="dc.id"
              >
                {{ formatAdminUserLabel(dc) }}
              </option>
            </select>
          </div>
        </div>

        <div class="modalFoot">
          <button class="btn" @click="closeDeactivateModal">Cancel</button>
          <button class="btn btn-primary" :disabled="saving" @click="confirmDeactivate">
            {{ saving ? "Deactivating..." : "Confirm Deactivate" }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="deleteModalOpen" class="overlay">
      <div class="modal">
        <div class="modalHead">
          <div>
            <div class="modalTitle">Delete User</div>
            <div class="modalSub">This removes the account from login and admin management, but keeps past logs and workflow history.</div>
          </div>
          <button class="btn btn-sm" @click="closeDeleteModal">Close</button>
        </div>

        <div class="modalBody formStack">
          <div v-if="deleteModalError" class="modalAlert modalAlertDanger">{{ deleteModalError }}</div>
          <div class="noticeLine">
            Deleting: <b>{{ deleteTargetUserLabel }}</b>
          </div>
          <div v-if="deleteTargetUser?.role === 'ADMIN'" class="adminWarning">
            This is an ADMIN account. Historical records will stay, but this account will be removed from login and admin management.
          </div>
          <div class="deleteNotice">
            Only already deactivated users can be deleted. Historical records such as movements, minutes, and audit logs will remain.
          </div>
          <div v-if="deleteRequiresTypedConfirmation" class="field">
            <label>Type Admin Username To Confirm</label>
            <input
              v-model="deleteConfirmUsername"
              class="input"
              :placeholder="`Type ${deleteTargetUser?.username || 'username'}`"
            />
          </div>
        </div>

        <div class="modalFoot">
          <button class="btn" @click="closeDeleteModal">Cancel</button>
          <button class="btn btn-danger" :disabled="saving || !deleteConfirmationValid" @click="confirmDeleteUser">
            {{ saving ? "Deleting..." : "Delete User" }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="bulkDeleteModalOpen" class="overlay">
      <div class="modal">
        <div class="modalHead">
          <div>
            <div class="modalTitle">Delete Selected Users</div>
            <div class="modalSub">Selected inactive users will be removed from login and admin management, while history stays preserved.</div>
          </div>
          <button class="btn btn-sm" @click="closeBulkDeleteModal">Close</button>
        </div>

        <div class="modalBody formStack">
          <div v-if="bulkDeleteModalError" class="modalAlert modalAlertDanger">{{ bulkDeleteModalError }}</div>
          <div class="noticeLine">
            Selected for deletion: <b>{{ selectedUsers.length }}</b>
          </div>
          <div class="deleteNotice">
            Only inactive users are selectable. Audit logs, movements, minutes, and workflow history will remain unchanged.
          </div>
        </div>

        <div class="modalFoot">
          <button class="btn" @click="closeBulkDeleteModal">Cancel</button>
          <button class="btn btn-danger" :disabled="saving || !selectedUsers.length" @click="confirmBulkDeleteUsers">
            {{ saving ? "Deleting..." : `Delete ${selectedUsers.length} Users` }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="bulkDeactivateModalOpen" class="overlay">
      <div class="modal">
        <div class="modalHead">
          <div>
            <div class="modalTitle">Deactivate Selected Users</div>
            <div class="modalSub">Each selected user will be deactivated only after active documents are transferred to the fallback DC.</div>
          </div>
          <button class="btn btn-sm" @click="closeBulkDeactivateModal">Close</button>
        </div>

        <div class="modalBody formStack">
          <div v-if="bulkDeactivateModalError" class="modalAlert modalAlertDanger">{{ bulkDeactivateModalError }}</div>
          <div class="noticeLine">
            Selected for deactivation: <b>{{ selectedUsers.length }}</b>
          </div>
          <div class="field">
            <label>Fallback DC</label>
            <select v-model.number="deactivateFallbackDcUserId" class="input">
              <option
                v-for="dc in availableFallbackDcs(null)"
                :key="`bulk-fallback-${dc.id}`"
                :value="dc.id"
              >
                {{ formatAdminUserLabel(dc) }}
              </option>
            </select>
          </div>
          <div class="deleteNotice">
              Only active non-admin users are selectable here. Their account stays in the system, and Report At assignment is moved safely before deactivation.
          </div>
        </div>

        <div class="modalFoot">
          <button class="btn" @click="closeBulkDeactivateModal">Cancel</button>
          <button class="btn btn-primary" :disabled="saving || !selectedUsers.length || !deactivateFallbackDcUserId" @click="confirmBulkDeactivateUsers">
            {{ saving ? "Deactivating..." : `Deactivate ${selectedUsers.length} Users` }}
          </button>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { Eye, EyeOff } from "lucide-vue-next";
import AppLayout from "../layouts/AppLayout.vue";
import { getCurrentUser } from "../auth/currentUser";
import { formatUserLabel } from "../auth/userLabel";
  import { useToast } from "../composables/useToast";
  import {
    adminActivateUser,
    adminBulkDeactivateUsers,
    adminBulkDeleteUsers,
    adminCreateUser,
    adminDeleteUser,
    adminDeactivateUser,
  adminListDuplicateUsers,
  adminListRoles,
  adminListUsers,
  adminMergeUsers,
  adminResetPassword,
  adminUpdateUser,
  adminGetUserPermissions,
  adminUpdateUserPermissions,
} from "../api/auth.api";

const toast = useToast();
const user = ref(getCurrentUser());
const isAdmin = computed(() => user.value?.role === "ADMIN");

const loading = ref(false);
const saving = ref(false);
const error = ref("");
const rows = ref([]);
const summaryRows = ref([]);
const roles = ref([]);
const page = ref(0);
const size = ref(20);
const last = ref(false);
const duplicateGroups = ref([]);

const search = ref("");
const role = ref("");
const active = ref("");
const createPanelOpen = ref(true);

const deactivateModalOpen = ref(false);
const deactivateTargetUser = ref(null);
const deactivateFallbackDcUserId = ref(null);
const fallbackDcOptions = ref([]);
const deleteModalOpen = ref(false);
const deleteTargetUser = ref(null);
const deleteConfirmUsername = ref("");
const deleteModalError = ref("");
const bulkDeleteModalOpen = ref(false);
const bulkDeactivateModalOpen = ref(false);
const bulkMode = ref("deactivate");
const selectedUserIds = ref([]);
const deactivateModalError = ref("");
const bulkDeleteModalError = ref("");
const bulkDeactivateModalError = ref("");

const editModalOpen = ref(false);
const editingUser = ref(null);
const editForm = ref({
  fullName: "",
  username: "",
  email: "",
  phone: "",
  department: "",
});

const resetPasswordModalOpen = ref(false);
const resetPasswordUser = ref(null);
const resetPasswordValue = ref("");
const showResetPassword = ref(false);
const showCreatePassword = ref(false);

const createForm = ref({
  fullName: "",
  username: "",
  email: "",
  phone: "",
  department: "",
  role: "",
  password: "",
});

const activeUsersCount = computed(() => summaryRows.value.filter((u) => u.active).length);
const inactiveUsersCount = computed(() => summaryRows.value.filter((u) => !u.active).length);
const editingUserLabel = computed(() => formatAdminUserLabel(editingUser.value));
const resetPasswordUserLabel = computed(() => formatAdminUserLabel(resetPasswordUser.value));

const permissionsModalOpen = ref(false);
const permissionsUser = ref(null);
const permissionsEntries = ref([]);
const permissionsLoading = ref(false);
const permissionsUserLabel = computed(() => formatAdminUserLabel(permissionsUser.value));
const deactivateTargetUserLabel = computed(() => formatAdminUserLabel(deactivateTargetUser.value));
const deleteTargetUserLabel = computed(() => formatAdminUserLabel(deleteTargetUser.value));
const deleteRequiresTypedConfirmation = computed(() => deleteTargetUser.value?.role === "ADMIN");
const deleteConfirmationValid = computed(() =>
  !deleteRequiresTypedConfirmation.value
    || deleteConfirmUsername.value.trim() === String(deleteTargetUser.value?.username || "").trim()
);
const activeRows = computed(() => rows.value.filter((u) => u.active && u.role !== "ADMIN"));
const inactiveRows = computed(() => rows.value.filter((u) => !u.active));
const eligibleUsers = computed(() =>
  summaryRows.value.filter((u) =>
    bulkMode.value === "deactivate" ? u.active && u.role !== "ADMIN" : !u.active
  )
);
const eligibleRows = computed(() => (bulkMode.value === "deactivate" ? activeRows.value : inactiveRows.value));
const selectedUsers = computed(() =>
  eligibleUsers.value.filter((u) => selectedUserIds.value.includes(Number(u.id)))
);
const allEligibleRowsSelected = computed(() =>
  eligibleUsers.value.length > 0 && eligibleUsers.value.every((u) => selectedUserIds.value.includes(Number(u.id)))
);
const someEligibleRowsSelected = computed(() =>
  eligibleUsers.value.some((u) => selectedUserIds.value.includes(Number(u.id)))
);
const bulkModeTitle = computed(() =>
  bulkMode.value === "deactivate" ? "Deactivate Multiple Users Safely" : "Delete Multiple Inactive Users Safely"
);
const bulkModeDescription = computed(() =>
  bulkMode.value === "deactivate"
              ? "Select active non-admin users, choose one fallback DC, and transfer active Report At assignment before deactivation."
    : "Select inactive users to remove from login and admin management while keeping historical records intact."
);
const bulkActionLabel = computed(() =>
  bulkMode.value === "deactivate"
    ? `Deactivate ${selectedUsers.value.length || ""} Selected`.trim()
    : `Delete ${selectedUsers.value.length || ""} Selected`.trim()
);

function normalizeBlank(value) {
  const v = String(value ?? "").trim();
  return v || null;
}

function isSelectableUser(userRecord) {
  if (!userRecord) return false;
  if (bulkMode.value === "deactivate") {
    return Boolean(userRecord.active) && userRecord.role !== "ADMIN";
  }
  return !userRecord.active;
}

function clearSelectedUsers() {
  selectedUserIds.value = [];
}

function setBulkMode(mode) {
  if (bulkMode.value === mode) return;
  bulkMode.value = mode;
}

function userInitials(u) {
  const text = String(u?.fullName || u?.username || "U").trim();
  const parts = text.split(/\s+/).slice(0, 2);
  return parts.map((part) => part[0]?.toUpperCase() || "").join("") || "U";
}

function resetCreateForm() {
  createForm.value = {
    fullName: "",
    username: "",
    email: "",
    phone: "",
    department: "",
    role: "",
    password: "",
  };
  showCreatePassword.value = false;
}

function toggleCreatePanel() {
  createPanelOpen.value = !createPanelOpen.value;
}

function resetFilters() {
  search.value = "";
  role.value = "";
  active.value = "";
  page.value = 0;
  load();
}

function applyFilters() {
  page.value = 0;
  load();
}

function goToPrevPage() {
  if (page.value === 0 || loading.value) return;
  page.value -= 1;
  load();
}

function goToNextPage() {
  if (last.value || loading.value) return;
  page.value += 1;
  load();
}

async function loadRoles() {
  try {
    roles.value = await adminListRoles();
  } catch {
    roles.value = ["ADMIN", "DC", "DDC", "SDDC", "SC", "ASC", "PMA"];
  }
}

async function load() {
  if (!isAdmin.value) return;
  loading.value = true;
  error.value = "";
  try {
    const params = {
      search: search.value || undefined,
      role: role.value || undefined,
      active: active.value === "" ? undefined : active.value,
    };

    const data = await adminListUsers({
      page: page.value,
      size: size.value,
      ...params,
    });

    rows.value = data?.content ?? [];
    summaryRows.value = rows.value;
    const uMeta = data?.page ?? data;
    const uTp = Number(uMeta?.totalPages ?? 0);
    last.value = data?.last ?? (uTp === 0 || page.value >= uTp - 1);
    selectedUserIds.value = selectedUserIds.value.filter((id) =>
      summaryRows.value.some((u) => Number(u.id) === Number(id) && isSelectableUser(u))
    );
    await loadDuplicates();
  } catch (e) {
    error.value = e?.message || "Failed to load users";
    rows.value = [];
    summaryRows.value = [];
    selectedUserIds.value = [];
  } finally {
    loading.value = false;
  }
}

async function loadDuplicates() {
  try {
    duplicateGroups.value = await adminListDuplicateUsers();
  } catch {
    duplicateGroups.value = [];
  }
}

async function createUser() {
  error.value = "";
  saving.value = true;
  try {
    await adminCreateUser({
      fullName: createForm.value.fullName,
      username: createForm.value.username,
      email: normalizeBlank(createForm.value.email),
      phone: normalizeBlank(createForm.value.phone),
      department: normalizeBlank(createForm.value.department),
      role: createForm.value.role,
      password: createForm.value.password,
    });
    resetCreateForm();
    createPanelOpen.value = false;
    toast.success("User created successfully.");
    await load();
  } catch (e) {
    error.value = e?.message || "Create user failed";
    toast.error(error.value);
  } finally {
    saving.value = false;
  }
}

function openEditModal(u) {
  editingUser.value = u;
  editForm.value = {
    fullName: u.fullName || "",
    username: u.username || "",
    email: u.email || "",
    phone: u.phone || "",
    department: u.department || "",
  };
  editModalOpen.value = true;
}

function closeEditModal() {
  editModalOpen.value = false;
  editingUser.value = null;
}

async function saveEditUser() {
  if (!editingUser.value) return;
  saving.value = true;
  error.value = "";
  try {
    await adminUpdateUser(editingUser.value.id, {
      fullName: editForm.value.fullName,
      username: editForm.value.username,
      email: normalizeBlank(editForm.value.email),
      phone: normalizeBlank(editForm.value.phone),
      department: normalizeBlank(editForm.value.department),
    });
    closeEditModal();
    toast.success("User updated successfully.");
    await load();
  } catch (e) {
    error.value = e?.message || "Update failed";
    toast.error(error.value);
  } finally {
    saving.value = false;
  }
}

function openResetPasswordModal(u) {
  resetPasswordUser.value = u;
  resetPasswordValue.value = "";
  showResetPassword.value = false;
  resetPasswordModalOpen.value = true;
}

function closeResetPasswordModal() {
  resetPasswordModalOpen.value = false;
  resetPasswordUser.value = null;
  resetPasswordValue.value = "";
  showResetPassword.value = false;
}

function openPermissionsModal(u) {
  permissionsUser.value = u;
  permissionsEntries.value = [];
  permissionsModalOpen.value = true;
  loadUserPermissions(u.id);
}

async function loadUserPermissions(userId) {
  permissionsLoading.value = true;
  try {
    const data = await adminGetUserPermissions(userId);
    permissionsEntries.value = (data.entries || []).map((e) => ({
      permission: e.permission,
      roleDefault: e.roleDefault,
      // Map the backend tri-state (override: null / true / false) to the select value.
      mode: e.override === null || e.override === undefined ? "INHERIT" : e.override ? "GRANT" : "REVOKE",
    }));
  } catch (e) {
    toast.error(String(e));
    closePermissionsModal();
  } finally {
    permissionsLoading.value = false;
  }
}

function closePermissionsModal() {
  permissionsModalOpen.value = false;
  permissionsUser.value = null;
  permissionsEntries.value = [];
}

async function savePermissions() {
  if (!permissionsUser.value) return;
  saving.value = true;
  try {
    const entries = permissionsEntries.value.map((e) => ({
      permission: e.permission,
      override: e.mode === "GRANT" ? true : e.mode === "REVOKE" ? false : null,
    }));
    await adminUpdateUserPermissions(permissionsUser.value.id, entries);
    toast.success("Permissions updated.");
    closePermissionsModal();
  } catch (e) {
    toast.error(String(e));
  } finally {
    saving.value = false;
  }
}

async function confirmResetPassword() {
  if (!resetPasswordUser.value || !resetPasswordValue.value.trim()) return;
  saving.value = true;
  error.value = "";
  try {
    await adminResetPassword(resetPasswordUser.value.id, resetPasswordValue.value.trim());
    closeResetPasswordModal();
    toast.success("Password reset successfully.");
  } catch (e) {
    error.value = e?.message || "Reset password failed";
    toast.error(error.value);
  } finally {
    saving.value = false;
  }
}

async function deactivateUser(u) {
  deactivateModalError.value = "";
  await loadFallbackDcOptions();
  const activeDcs = availableFallbackDcs(u.id);
  if (activeDcs.length === 0) {
    error.value = "No active fallback DC user available.";
    toast.warning(error.value);
    return;
  }

  // Deactivation needs a fallback DC because active Report At assignments cannot point to inactive users.
  deactivateTargetUser.value = u;
  deactivateFallbackDcUserId.value = activeDcs[0]?.id ?? null;
  deactivateModalOpen.value = true;
}

async function loadFallbackDcOptions() {
  try {
    const data = await adminListUsers({ page: 0, size: 200, role: "DC", active: true });
    fallbackDcOptions.value = Array.isArray(data?.content) ? data.content : [];
  } catch {
    fallbackDcOptions.value = [];
  }
}

function availableFallbackDcs(excludeUserId) {
  return fallbackDcOptions.value.filter((x) => x.active && x.role === "DC" && Number(x.id) !== Number(excludeUserId));
}

function closeDeactivateModal() {
  deactivateModalOpen.value = false;
  deactivateTargetUser.value = null;
  deactivateFallbackDcUserId.value = null;
  fallbackDcOptions.value = [];
  deactivateModalError.value = "";
}

function openDeleteModal(u) {
  deleteTargetUser.value = u;
  deleteConfirmUsername.value = "";
  deleteModalError.value = "";
  deleteModalOpen.value = true;
}

function closeDeleteModal() {
  deleteModalOpen.value = false;
  deleteTargetUser.value = null;
  deleteConfirmUsername.value = "";
  deleteModalError.value = "";
}

function openBulkDeleteModal() {
  if (bulkMode.value !== "delete" || !selectedUsers.value.length) return;
  bulkDeleteModalError.value = "";
  bulkDeleteModalOpen.value = true;
}

async function openBulkDeactivateModal() {
  if (bulkMode.value !== "deactivate" || !selectedUsers.value.length) return;
  bulkDeactivateModalError.value = "";
  await loadFallbackDcOptions();
  const activeDcs = availableFallbackDcs(null);
  if (activeDcs.length === 0) {
    error.value = "No active fallback DC user available.";
    toast.warning(error.value);
    return;
  }
  deactivateFallbackDcUserId.value = activeDcs[0]?.id ?? null;
  bulkDeactivateModalOpen.value = true;
}

function closeBulkDeleteModal() {
  bulkDeleteModalOpen.value = false;
  bulkDeleteModalError.value = "";
}

function closeBulkDeactivateModal() {
  bulkDeactivateModalOpen.value = false;
  deactivateFallbackDcUserId.value = null;
  fallbackDcOptions.value = [];
  bulkDeactivateModalError.value = "";
}

function openBulkActionModal() {
  if (bulkMode.value === "deactivate") {
    openBulkDeactivateModal();
    return;
  }
  openBulkDeleteModal();
}

function selectAllEligibleRows() {
  // Bulk selection is limited to rows that the current bulk mode is allowed to modify.
  selectedUserIds.value = Array.from(new Set(eligibleUsers.value.map((u) => Number(u.id))));
}

function isSelected(userId) {
  return selectedUserIds.value.includes(Number(userId));
}

function toggleSelectedUser(userId) {
  const id = Number(userId);
  const row = rows.value.find((userRecord) => Number(userRecord.id) === id);
  if (!isSelectableUser(row)) return;
  if (selectedUserIds.value.includes(id)) {
    selectedUserIds.value = selectedUserIds.value.filter((value) => value !== id);
    return;
  }
  selectedUserIds.value = [...selectedUserIds.value, id];
}

function toggleSelectAllEligibleRows(event) {
  const checked = Boolean(event?.target?.checked);
  const eligibleIds = eligibleUsers.value.map((u) => Number(u.id));
  if (checked) {
    selectedUserIds.value = Array.from(new Set([...selectedUserIds.value, ...eligibleIds]));
    return;
  }
  selectedUserIds.value = selectedUserIds.value.filter((id) => !eligibleIds.includes(Number(id)));
}

async function confirmDeactivate() {
  if (!deactivateTargetUser.value || !deactivateFallbackDcUserId.value) return;
  saving.value = true;
  error.value = "";
  deactivateModalError.value = "";
  try {
    await adminDeactivateUser(deactivateTargetUser.value.id, Number(deactivateFallbackDcUserId.value));
    closeDeactivateModal();
    toast.success("User deactivated successfully.");
    await load();
  } catch (e) {
    error.value = e?.message || "Deactivate failed";
    deactivateModalError.value = error.value;
    toast.error(error.value);
  } finally {
    saving.value = false;
  }
}

function mergeTargetId(groupUsers, sourceId) {
  return groupUsers.find((u) => Number(u.id) !== Number(sourceId) && u.active)?.id ?? null;
}

function formatAdminUserLabel(user) {
  if (!user) return "-";
  const id = user.id == null ? "ID unknown" : `ID ${user.id}`;
  return `${formatUserLabel(user)} • ${id}`;
}

function mergeTargetLabel(groupUsers, sourceId) {
  const targetId = mergeTargetId(groupUsers, sourceId);
  const target = groupUsers.find((u) => Number(u.id) === Number(targetId));
  return target ? formatAdminUserLabel(target) : "-";
}

async function mergeDuplicate(sourceUserId, targetUserId) {
  if (!sourceUserId || !targetUserId) return;
  error.value = "";
  saving.value = true;
  try {
    await adminMergeUsers(Number(sourceUserId), Number(targetUserId));
    toast.success("Duplicate user merged successfully.");
    await load();
  } catch (e) {
    error.value = e?.message || "Merge failed";
    toast.error(error.value);
  } finally {
    saving.value = false;
  }
}

async function activateUser(u) {
  error.value = "";
  saving.value = true;
  try {
    await adminActivateUser(u.id);
    toast.success("User activated successfully.");
    await load();
  } catch (e) {
    error.value = e?.message || "Activate failed";
    toast.error(error.value);
  } finally {
    saving.value = false;
  }
}

async function confirmDeleteUser() {
  if (!deleteTargetUser.value) return;
  error.value = "";
  saving.value = true;
  deleteModalError.value = "";
  try {
    await adminDeleteUser(deleteTargetUser.value.id);
    selectedUserIds.value = selectedUserIds.value.filter((id) => id !== Number(deleteTargetUser.value.id));
    closeDeleteModal();
    toast.success("User deleted safely. Historical records were kept.");
    if (rows.value.length === 1 && page.value > 0) {
      page.value -= 1;
    }
    await load();
  } catch (e) {
    error.value = e?.message || "Delete failed";
    deleteModalError.value = error.value;
    toast.error(error.value);
  } finally {
    saving.value = false;
  }
}

async function confirmBulkDeleteUsers() {
  if (bulkMode.value !== "delete" || !selectedUsers.value.length) return;
  error.value = "";
  saving.value = true;
  bulkDeleteModalError.value = "";
  try {
    const ids = selectedUsers.value.map((u) => Number(u.id));
    await adminBulkDeleteUsers(ids);
    closeBulkDeleteModal();
    selectedUserIds.value = [];
    toast.success(`Deleted ${ids.length} inactive user${ids.length === 1 ? "" : "s"} safely. Historical records were kept.`);
    if (rows.value.length === ids.length && page.value > 0) {
      page.value -= 1;
    }
    await load();
  } catch (e) {
    error.value = e?.message || "Bulk delete failed";
    bulkDeleteModalError.value = error.value;
    toast.error(error.value);
  } finally {
    saving.value = false;
  }
}

async function confirmBulkDeactivateUsers() {
  if (bulkMode.value !== "deactivate" || !selectedUsers.value.length || !deactivateFallbackDcUserId.value) return;
  error.value = "";
  saving.value = true;
  bulkDeactivateModalError.value = "";
  try {
    const ids = selectedUsers.value.map((u) => Number(u.id));
    await adminBulkDeactivateUsers(ids, Number(deactivateFallbackDcUserId.value));
    closeBulkDeactivateModal();
    selectedUserIds.value = selectedUserIds.value.filter((id) => !ids.includes(Number(id)));
      toast.success(`Deactivated ${ids.length} user${ids.length === 1 ? "" : "s"} safely and transferred active Report At assignment.`);
    await load();
  } catch (e) {
    error.value = e?.message || "Bulk deactivate failed";
    bulkDeactivateModalError.value = error.value;
    toast.error(error.value);
  } finally {
    saving.value = false;
  }
}

async function downloadCsv() {
  try {
    const data = await adminListUsers({
      page: 0,
      size: 10000,
      search: search.value || undefined,
      role: role.value || undefined,
      active: active.value === "" ? undefined : active.value,
    });
    const rowsData = data?.content ?? [];
    const header = ["id", "fullName", "username", "email", "phone", "department", "role", "active", "createdAt"];
    const body = rowsData.map((u) => [u.id, u.fullName, u.username, u.email || "", u.phone || "", u.department || "", u.role, u.active, u.createdAt]);
    const csv = [header, ...body].map((line) => line.map((v) => `"${String(v ?? "").replaceAll('"', '""')}"`).join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
    // Object URL keeps export client-side; revoke immediately after triggering the download.
    const href = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = href;
    a.download = "users-export.csv";
    a.click();
    URL.revokeObjectURL(href);
    toast.success("User export downloaded.");
  } catch (e) {
    error.value = e?.message || "Export failed";
    toast.error(error.value);
  }
}

watch(bulkMode, () => {
  clearSelectedUsers();
  closeBulkDeleteModal();
  closeBulkDeactivateModal();
});

onMounted(async () => {
  if (!isAdmin.value) return;
  await loadRoles();
  await load();
});
</script>

<style scoped>
.usersPage {
  position: relative;
  padding: 4px 2px 0;
}

.pageGlow {
  position: absolute;
  border-radius: 999px;
  filter: blur(44px);
  z-index: 0;
  pointer-events: none;
  opacity: 0.55;
}

.pageGlowA {
  width: 220px;
  height: 220px;
  right: 2%;
  top: -12px;
  background: radial-gradient(circle at center, rgba(37, 99, 235, 0.3), rgba(37, 99, 235, 0));
}

.pageGlowB {
  width: 180px;
  height: 180px;
  left: 6%;
  top: 42%;
  background: radial-gradient(circle at center, rgba(14, 116, 144, 0.22), rgba(14, 116, 144, 0));
}

.pageHead {
  position: relative;
  z-index: 1;
  display:flex;
  align-items:flex-start;
  justify-content:space-between;
  gap: 12px;
  margin-bottom:14px;
}

h2 {
  margin: 0;
  font-size: 1.45rem;
  line-height: 1.15;
  letter-spacing: -0.02em;
  color: #0f172a;
}

.pageSub {
  margin: 6px 0 0;
  color: #475569;
  font-size: 13px;
  max-width: 760px;
}

.headActions {
  display:flex;
  gap:8px;
  flex-wrap:wrap;
}

.statsGrid {
  position: relative;
  z-index: 1;
  display:grid;
  grid-template-columns:repeat(4, minmax(0, 1fr));
  gap:12px;
  margin-bottom:14px;
}

.statCard {
  background: linear-gradient(160deg, #ffffff 0%, #f8fbff 100%);
  border: 1px solid #dbe8ff;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
  padding: 16px;
  border-radius: 14px;
  display:flex;
  flex-direction:column;
  gap:4px;
}

.statCard.warn {
  border-color:#fde68a;
  background:linear-gradient(160deg, #fffdf3 0%, #fffbeb 100%);
}

.statLabel {
  font-size:12px;
  text-transform:uppercase;
  letter-spacing:0.08em;
  color:#475569;
  font-weight:800;
}

.statValue {
  font-size:30px;
  line-height:1;
  color:#0f172a;
}

.statMeta {
  color:#64748b;
  font-size:12px;
}

.card {
  position: relative;
  z-index: 1;
  background: linear-gradient(160deg, #ffffff 0%, #f8fbff 100%);
  border: 1px solid #dbe8ff;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
  padding: 20px;
  border-radius: 14px;
}

.section {
  border:1px solid #e2e8f0;
  border-radius:14px;
  background:#ffffff;
  padding:16px;
  margin-bottom:14px;
}

.createSection {
  background:linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
  border-color:#dbeafe;
}

.dupSection {
  margin-bottom:0;
}

.sectionHead {
  display:flex;
  align-items:flex-start;
  justify-content:space-between;
  gap:12px;
  margin-bottom:12px;
}

.sectionHead h3 {
  margin:0;
  color:#0f172a;
  font-size:18px;
}

.sectionHead p {
  margin:4px 0 0;
  color:#64748b;
  font-size:13px;
}

.sectionEyebrow {
  color:#2563eb;
  font-size:11px;
  font-weight:900;
  letter-spacing:0.08em;
  text-transform:uppercase;
  margin-bottom:5px;
}

.tableMeta {
  color:#64748b;
  font-size:12px;
  font-weight:700;
}

.filters {
  display:grid;
  grid-template-columns:2fr 1fr 1fr auto;
  gap:10px;
  align-items:end;
}

.field {
  display:flex;
  flex-direction:column;
  gap:6px;
}

.field label {
  font-size:12px;
  font-weight:700;
  color:#334155;
}

.wide {
  min-width:0;
}

.filterActions {
  display:flex;
  align-items:flex-end;
}

.createGrid {
  display:grid;
  grid-template-columns:repeat(4, minmax(0, 1fr));
  gap:10px;
  align-items:end;
}

.createActions {
  display:flex;
  gap:8px;
  align-items:flex-end;
}

.tableWrap {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: auto;
}

.table {
  width:100%;
  border-collapse:collapse;
  table-layout:fixed;
}

.col-select { width: 46px; }
.col-user { width: 290px; }
.col-contact { width: 220px; }
.col-department { width: 170px; }
.col-status { width: 110px; }
.col-actions { width: 260px; }

.table th,
.table td {
  padding:12px 10px;
  border-bottom:1px solid #e5e7eb;
  text-align:left;
  vertical-align:top;
}

.table th {
  background: #f8fafc;
  font-size: 12px;
  color:#475569;
  text-transform: uppercase;
  letter-spacing:0.04em;
}

.selectCell {
  text-align:center;
}

.bulkPanel {
  margin-bottom:12px;
  padding:14px;
  border-radius:14px;
  border:1px solid #dbeafe;
  background:linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}

.bulkPanelInfo {
  border-color:#bfdbfe;
  background:linear-gradient(180deg, #eff6ff 0%, #ffffff 100%);
}

.bulkPanelDanger {
  border-color:#fecaca;
  background:linear-gradient(180deg, #fff7f7 0%, #ffffff 100%);
}

.bulkPanelHead {
  display:flex;
  align-items:flex-start;
  justify-content:space-between;
  gap:16px;
}

.bulkPanelHead h4 {
  margin:0;
  color:#0f172a;
  font-size:16px;
}

.bulkPanelHead p {
  margin:4px 0 0;
  color:#64748b;
  font-size:13px;
  max-width:700px;
}

.bulkModeSwitch {
  display:flex;
  gap:8px;
  flex-wrap:wrap;
}

.modeBtn {
  padding:9px 12px;
  border-radius:999px;
  border:1px solid #d1d5db;
  background:#fff;
  color:#334155;
  font-size:12px;
  font-weight:800;
  cursor:pointer;
}

.modeBtnActive {
  color:#fff;
}

.modeBtnPrimary {
  background:#2563eb;
  border-color:#2563eb;
}

.modeBtnDanger {
  background:#dc2626;
  border-color:#dc2626;
}

.bulkPanelBody {
  margin-top:14px;
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:12px;
  flex-wrap:wrap;
}

.bulkStats {
  display:flex;
  align-items:center;
  gap:10px;
  flex-wrap:wrap;
}

.bulkStat {
  display:inline-flex;
  align-items:center;
  gap:4px;
  border:1px solid #dbeafe;
  background:#ffffff;
  border-radius:999px;
  padding:7px 10px;
  color:#334155;
  font-size:12px;
}

.bulkPanelDanger .bulkStat {
  border-color:#fecaca;
}

.bulkActionsBar {
  display:flex;
  align-items:center;
  gap:8px;
  flex-wrap:wrap;
}

.userCell {
  display:flex;
  align-items:flex-start;
  gap:10px;
}

.userAvatar {
  width:38px;
  height:38px;
  border-radius:999px;
  display:grid;
  place-items:center;
  flex:0 0 auto;
  background:#dbeafe;
  color:#1e3a8a;
  font-size:12px;
  font-weight:900;
}

.userInfo {
  min-width:0;
}

.userName {
  color:#0f172a;
  font-size:14px;
  font-weight:800;
  line-height:1.25;
}

.userMeta,
.contactSub {
  color:#64748b;
  font-size:12px;
}

.contactLine {
  color:#0f172a;
  font-size:13px;
  overflow:hidden;
  text-overflow:ellipsis;
}

.truncateText {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.muted,
.emptyPanel {
  color:#6b7280;
  text-align:center;
}

.emptyPanel {
  padding:18px;
  border:1px dashed #cbd5e1;
  border-radius:12px;
  background:#f8fafc;
}

.input {
  height:40px;
  border-radius:10px;
  border:1px solid #d1d5db;
  background:#fff;
  padding:0 10px;
  outline:none;
  width:100%;
  box-sizing:border-box;
  transition: border-color .2s ease, box-shadow .2s ease;
}

.input:focus {
  border-color:#2563eb;
  box-shadow:0 0 0 3px rgba(37, 99, 235, 0.14);
}

.passwordField {
  position:relative;
}

.passwordInput {
  padding-right:44px;
}

.passwordToggle {
  position:absolute;
  top:50%;
  right:6px;
  width:32px;
  height:32px;
  display:inline-flex;
  align-items:center;
  justify-content:center;
  transform:translateY(-50%);
  border:0;
  border-radius:8px;
  background:transparent;
  color:#64748b;
  cursor:pointer;
}

.passwordToggle:hover {
  background:#eff6ff;
  color:#1d4ed8;
}

.passwordToggle:focus-visible {
  outline:2px solid #2563eb;
  outline-offset:2px;
}

.passwordIcon {
  width:18px;
  height:18px;
}

.btn {
  padding:10px 12px;
  border-radius:10px;
  border:1px solid #d1d5db;
  background:#fff;
  cursor:pointer;
  transition: all .2s ease;
}

.btn:hover:not(:disabled) {
  background:#f9fafb;
}

.btn:disabled {
  opacity:0.6;
  cursor:not-allowed;
}

.btn-primary {
  background:#2563eb;
  border-color:#2563eb;
  color:#fff;
}

.btn-primary:hover:not(:disabled) {
  background:#1d4ed8;
}

.btn-sm {
  padding:6px 8px;
  font-size:12px;
}

.actions {
  display:flex;
  gap:6px;
  flex-wrap:wrap;
}

.pill {
  display:inline-block;
  padding:4px 9px;
  border-radius:999px;
  font-size:11px;
  font-weight:700;
}

.pill-active {
  background:#ecfdf5;
  color:#065f46;
  border:1px solid #a7f3d0;
}

.pill-inactive {
  background:#fef2f2;
  color:#991b1b;
  border:1px solid #fecaca;
}

.danger {
  border-color:#fecaca;
  color:#991b1b;
}

.ghostDanger {
  background:#fff7f7;
}

.btn-danger {
  background:#dc2626;
  border-color:#dc2626;
  color:#fff;
}

.btn-danger:hover:not(:disabled) {
  background:#b91c1c;
}

.errorBox {
  margin-bottom:12px;
  background:#fef2f2;
  border:1px solid #fecaca;
  color:#991b1b;
  padding:10px 12px;
  border-radius:8px;
}

.pager {
  display:flex;
  align-items:center;
  justify-content:flex-end;
  gap:10px;
  margin-top:12px;
}

.dupGrid {
  display:grid;
  gap:10px;
}

.dupGroup {
  border:1px solid #dbeafe;
  border-radius:12px;
  background:#f8fbff;
  padding:12px;
}

.dupHead {
  font-weight:800;
  color:#1e3a8a;
  margin-bottom:10px;
}

.dupRows {
  display:flex;
  flex-direction:column;
  gap:8px;
}

.dupRow {
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:10px;
  border:1px solid #e5e7eb;
  border-radius:10px;
  background:#fff;
  padding:10px 12px;
}

.dupName {
  color:#0f172a;
  font-size:13px;
  font-weight:700;
}

.dupMeta {
  color:#64748b;
  font-size:12px;
  margin-top:2px;
}

.overlay {
  position:fixed;
  inset:0;
  background:rgba(15, 23, 42, 0.42);
  backdrop-filter:blur(2px);
  display:flex;
  align-items:center;
  justify-content:center;
  padding:14px;
  z-index: 2000;
}

.modal {
  width:100%;
  max-width:620px;
  min-width:0;
  box-sizing:border-box;
  background:#fff;
  border-radius:14px;
  overflow:hidden;
  border:1px solid #e5e7eb;
  box-shadow:0 24px 60px rgba(15, 23, 42, 0.24);
}

.modalHead {
  display:flex;
  align-items:flex-start;
  justify-content:space-between;
  gap:12px;
  min-width:0;
  padding:14px 16px;
  border-bottom:1px solid #eee;
  background:linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.modalHead > div:first-child {
  flex:1 1 auto;
  min-width:0;
  max-width:100%;
}

.modalTitle {
  font-size:15px;
  font-weight:800;
  color:#0f172a;
  max-width:100%;
  overflow-wrap:anywhere;
  word-break:break-word;
}

.modalSub {
  font-size:12px;
  color:#6b7280;
  margin-top:2px;
  max-width:100%;
  overflow-wrap:anywhere;
  word-break:break-word;
}

.modalBody {
  padding:14px 16px;
}

.formStack {
  display:flex;
  flex-direction:column;
  gap:12px;
}

.modalAlert {
  padding:12px;
  border-radius:12px;
  font-size:13px;
  line-height:1.5;
}

.modalAlertDanger {
  border:1px solid #fecaca;
  background:#fff1f2;
  color:#9f1239;
}

.noticeLine {
  color:#334155;
  font-size:13px;
  overflow-wrap:anywhere;
  word-break:break-word;
}

.deleteNotice {
  border:1px solid #fecaca;
  background:#fff7f7;
  color:#7f1d1d;
  padding:12px;
  border-radius:12px;
  font-size:13px;
  line-height:1.5;
  overflow-wrap:anywhere;
  word-break:break-word;
}

.adminWarning {
  border:1px solid #fde68a;
  background:#fffbeb;
  color:#92400e;
  padding:12px;
  border-radius:12px;
  font-size:13px;
  line-height:1.5;
  overflow-wrap:anywhere;
  word-break:break-word;
}

.modalFoot {
  padding:14px 16px;
  border-top:1px solid #eee;
  display:flex;
  justify-content:flex-end;
  gap:8px;
  background:#f8fafc;
}

@media (max-width: 1100px) {
  .statsGrid {
    grid-template-columns:repeat(2, minmax(0, 1fr));
  }

  .filters {
    grid-template-columns:1fr 1fr;
  }

  .createGrid {
    grid-template-columns:repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
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

  .statsGrid,
  .filters,
  .createGrid {
    grid-template-columns:1fr;
  }

  .filterActions,
  .createActions {
    display:grid;
    grid-template-columns:1fr;
  }

  .dupRow,
  .sectionHead {
    flex-direction:column;
    align-items:flex-start;
  }

  .pager {
    justify-content:space-between;
  }
}
</style>
