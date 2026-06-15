<template>
  <AppLayout>
    <div class="profileShell">
      <section class="profileHero">
        <div class="heroGlow"></div>
        <div class="heroIdentity">
          <div class="heroAvatar">
            <img
              v-if="avatarUrl && !avatarBroken"
              :src="avatarUrl"
              alt="Profile picture"
              @error="avatarBroken = true"
            />
            <span v-else>{{ initials }}</span>
          </div>
          <div>
            <span class="eyebrow">Account Center</span>
            <h2>{{ displayName }}</h2>
            <p class="pageSub">Manage your personal details, account image, and sign-in security from one place.</p>
          </div>
        </div>
        <div class="heroMeta">
          <span class="statusDot"></span>
          <span>{{ roleDisplayName }}</span>
          <strong>{{ profile.department || "No department assigned" }}</strong>
        </div>
      </section>

      <div class="profileGrid">
        <aside class="profileSide">
          <section class="settingsPanel photoPanel">
            <div class="sectionHead">
              <div>
                <span class="eyebrow">Identity</span>
                <h3>Profile Photo</h3>
              </div>
              <HoverHint text="Allowed formats: JPG, PNG, WEBP. Max size: 5MB." />
            </div>

            <div class="photoPreview">
              <div class="photoRing">
                <img
                  v-if="avatarUrl && !avatarBroken"
                  :src="avatarUrl"
                  class="avatar"
                  alt="Profile picture"
                  @error="avatarBroken = true"
                />
                <div v-else class="avatar fallback">{{ initials }}</div>
              </div>
              <p class="photoHint">This image appears in the top header after sign in.</p>
            </div>

            <input ref="fileInput" type="file" accept="image/png,image/jpeg,image/webp" class="hidden" @change="onPick" />
            <div class="picActions">
              <button type="button" class="btn btn-ghost" @click="pickFile" :disabled="savingPic">Choose Image</button>
              <button type="button" class="btn btn-primary" :disabled="savingPic || !selectedPic" @click="uploadPicture">
                {{ savingPic ? "Uploading..." : "Upload Photo" }}
              </button>
            </div>
            <p class="selectedFile" :class="{ muted: !selectedFileName }">
              {{ selectedFileName || "No image selected yet" }}
            </p>
            <div v-if="selectedPicPreviewUrl" class="selectedImagePreview">
              <span class="previewLabel">Selected image preview</span>
              <img :src="selectedPicPreviewUrl" alt="Selected profile preview" />
            </div>
          </section>

          <section class="settingsPanel compactPanel">
            <div class="sectionHead">
              <div>
                <span class="eyebrow">Access</span>
                <h3>Account Info</h3>
              </div>
            </div>
            <dl class="accountList">
              <div>
                <dt>Username</dt>
                <dd>{{ profile.username || "-" }}</dd>
              </div>
              <div>
                <dt>Role</dt>
                <dd>{{ roleDisplayName }}</dd>
              </div>
              <div>
                <dt>Department</dt>
                <dd>{{ profile.department || "-" }}</dd>
              </div>
            </dl>
          </section>
        </aside>

        <main class="profileMain">
          <section class="settingsPanel">
            <div class="sectionHead panelSplit">
              <div>
                <span class="eyebrow">Personal Details</span>
                <h3>Basic Details</h3>
                <p class="sectionSub">Keep your contact details up to date for internal communication.</p>
              </div>
              <span class="safeBadge">Editable</span>
            </div>

            <div class="formGrid">
              <label class="formRow">
                <span>Username</span>
                <input
                  class="input inputReadonly"
                  :value="profile.username"
                  disabled
                  title="Username cannot be changed."
                />
              </label>
              <label class="formRow">
                <span>Role</span>
                <input
                  class="input inputReadonly"
                  :value="roleDisplayName"
                  disabled
                  title="Role can only be changed by ADMIN."
                />
              </label>
              <label class="formRow">
                <span>Full Name</span>
                <input class="input" v-model="profile.fullName" placeholder="Enter your full name" />
              </label>
              <label class="formRow">
                <span>Email</span>
                <input class="input" v-model="profile.email" type="email" maxlength="150" placeholder="name@example.com" />
              </label>
              <label class="formRow">
                <span>Phone</span>
                <input
                  class="input"
                  v-model="profile.phone"
                  inputmode="tel"
                  maxlength="30"
                  placeholder="Contact number"
                  @input="onPhoneInput"
                />
              </label>
              <label class="formRow">
                <span>Department</span>
                <input
                  class="input inputReadonly"
                  :value="profile.department"
                  disabled
                  title="Department can only be changed by ADMIN."
                />
              </label>
            </div>

            <div class="helperNote">
              <span>Department changes are handled by ADMIN in user management.</span>
              <HoverHint text="Department can only be changed by ADMIN in user management." />
            </div>

            <div class="btnRow">
              <button type="button" class="btn btn-ghost" :disabled="savingProfile" @click="load">Reset</button>
              <button type="button" class="btn btn-primary" :disabled="savingProfile" @click="saveProfile">
                {{ savingProfile ? "Saving..." : "Save Profile" }}
              </button>
            </div>
          </section>

          <section class="settingsPanel securityPanel">
            <div class="sectionHead panelSplit">
              <div>
                <span class="eyebrow">Security</span>
                <h3>Change Password</h3>
                <p class="sectionSub">Use at least 8 characters with letters and numbers.</p>
              </div>
              <span class="lockBadge">Protected</span>
            </div>

            <div class="formGrid passwordGrid">
              <label class="formRow">
                <span>Current Password</span>
                <div class="passwordField">
                  <input
                    class="input passwordInput"
                    v-model="password.currentPassword"
                    :type="showCurrentPassword ? 'text' : 'password'"
                    autocomplete="current-password"
                  />
                  <button
                    type="button"
                    class="passwordToggle"
                    :aria-label="showCurrentPassword ? 'Hide current password' : 'Show current password'"
                    :aria-pressed="showCurrentPassword"
                    @click="showCurrentPassword = !showCurrentPassword"
                  >
                    <EyeOff v-if="showCurrentPassword" class="passwordIcon" aria-hidden="true" />
                    <Eye v-else class="passwordIcon" aria-hidden="true" />
                  </button>
                </div>
              </label>
              <label class="formRow">
                <span>New Password</span>
                <div class="passwordField">
                  <input
                    class="input passwordInput"
                    v-model="password.newPassword"
                    :type="showNewPassword ? 'text' : 'password'"
                    autocomplete="new-password"
                    placeholder="At least 8 chars with letters and numbers"
                  />
                  <button
                    type="button"
                    class="passwordToggle"
                    :aria-label="showNewPassword ? 'Hide new password' : 'Show new password'"
                    :aria-pressed="showNewPassword"
                    @click="showNewPassword = !showNewPassword"
                  >
                    <EyeOff v-if="showNewPassword" class="passwordIcon" aria-hidden="true" />
                    <Eye v-else class="passwordIcon" aria-hidden="true" />
                  </button>
                </div>
              </label>
              <label class="formRow">
                <span>Confirm New Password</span>
                <div class="passwordField">
                  <input
                    class="input passwordInput"
                    v-model="password.confirmPassword"
                    :type="showConfirmPassword ? 'text' : 'password'"
                    autocomplete="new-password"
                  />
                  <button
                    type="button"
                    class="passwordToggle"
                    :aria-label="showConfirmPassword ? 'Hide confirm password' : 'Show confirm password'"
                    :aria-pressed="showConfirmPassword"
                    @click="showConfirmPassword = !showConfirmPassword"
                  >
                    <EyeOff v-if="showConfirmPassword" class="passwordIcon" aria-hidden="true" />
                    <Eye v-else class="passwordIcon" aria-hidden="true" />
                  </button>
                </div>
              </label>
            </div>

            <div class="passwordRule">
              <span></span>
              <p>A strong password protects document approvals, forwarding, and internal workflow actions.</p>
            </div>

            <div class="btnRow">
              <button type="button" class="btn btn-primary" :disabled="savingPassword" @click="savePassword">
                {{ savingPassword ? "Updating..." : "Update Password" }}
              </button>
            </div>
          </section>
        </main>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, onUnmounted, ref } from "vue";
import { Eye, EyeOff } from "lucide-vue-next";
import AppLayout from "../layouts/AppLayout.vue";
import HoverHint from "../components/HoverHint.vue";
import { useToast } from "../composables/useToast";
import {
  createMyProfilePictureUrl,
  changeMyPassword,
  getMe,
  updateMe,
  uploadMyProfilePicture,
} from "../api/auth.api";
import { getCurrentUser, updateSessionUser } from "../auth/currentUser";

const { success, error } = useToast();

const fileInput = ref(null);
const selectedPic = ref(null);
const selectedPicPreviewUrl = ref("");
const savingPic = ref(false);
const savingProfile = ref(false);
const savingPassword = ref(false);
const avatarBroken = ref(false);
const avatarVersion = ref("");
const avatarUrl = ref("");
const showCurrentPassword = ref(false);
const showNewPassword = ref(false);
const showConfirmPassword = ref(false);

const profile = ref({
  id: null,
  username: "",
  role: "",
  fullName: "",
  email: "",
  phone: "",
  department: "",
  hasProfilePicture: false,
  profilePictureUpdatedAt: null,
});

const password = ref({
  currentPassword: "",
  newPassword: "",
  confirmPassword: "",
});

const ROLE_LABELS = {
  ADMIN: "Administrator",
  DC: "Director of Customs",
  DDC: "Deputy Director of Customs",
  SDDC: "Senior Deputy Director of Customs",
  SC: "Superintendent of Customs",
  ASC: "Assistant Superintendent of Customs",
  PMA: "Personal Management Assistant",
};

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_ALLOWED_PATTERN = /^[0-9+\-()\s]*$/;

const initials = computed(() => {
  const text = String(profile.value.fullName || profile.value.username || "U").trim();
  if (!text) return "U";
  const parts = text.split(/\s+/).slice(0, 2);
  return parts.map((p) => p[0]?.toUpperCase() || "").join("") || "U";
});

const displayName = computed(() => profile.value.fullName || profile.value.username || "My Profile");
const roleDisplayName = computed(() => {
  const role = String(profile.value.role || "").trim().toUpperCase();
  return ROLE_LABELS[role] || profile.value.role || "User Account";
});
const selectedFileName = computed(() => selectedPic.value?.name || "");

let avatarRequestId = 0;

function clearSelectedPicPreview() {
  if (selectedPicPreviewUrl.value) {
    URL.revokeObjectURL(selectedPicPreviewUrl.value);
    selectedPicPreviewUrl.value = "";
  }
}

async function refreshAvatarUrl() {
  const requestId = ++avatarRequestId;
  avatarBroken.value = false;

  if (!profile.value.hasProfilePicture) {
    avatarUrl.value = "";
    return;
  }

  try {
    const url = await createMyProfilePictureUrl(avatarVersion.value || profile.value.profilePictureUpdatedAt || Date.now());
    if (requestId === avatarRequestId) avatarUrl.value = url;
  } catch {
    if (requestId === avatarRequestId) avatarUrl.value = "";
  }
}

function resetPasswordForm() {
  password.value = {
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  };
  showCurrentPassword.value = false;
  showNewPassword.value = false;
  showConfirmPassword.value = false;
}

function applySessionUser(data) {
  updateSessionUser({
    id: data.id,
    username: data.username,
    fullName: data.fullName,
    name: data.fullName,
    role: data.role,
    email: data.email,
    phone: data.phone,
    department: data.department,
    hasProfilePicture: !!data.hasProfilePicture,
    profilePictureUpdatedAt: data.profilePictureUpdatedAt || null,
    permissions: data.permissions || [],
  });
}

async function load() {
  try {
    const me = await getMe();
    profile.value = {
      id: me.id,
      username: me.username,
      role: me.role,
      fullName: me.fullName || "",
      email: me.email || "",
      phone: me.phone || "",
      department: me.department || "",
      hasProfilePicture: !!me.hasProfilePicture,
      profilePictureUpdatedAt: me.profilePictureUpdatedAt || null,
    };
    avatarBroken.value = false;
    avatarVersion.value = String(me.profilePictureUpdatedAt || Date.now());

    applySessionUser(me);
    await refreshAvatarUrl();
  } catch (e) {
    error(e?.message || "Failed to load profile.");
  }
}

function pickFile() {
  if (!fileInput.value) return;
  fileInput.value.click();
}

function onPick(event) {
  const file = event?.target?.files?.[0] || null;
  clearSelectedPicPreview();
  selectedPic.value = file;
  // Show a local preview before upload; the file is not sent until Upload Photo is clicked.
  selectedPicPreviewUrl.value = file ? URL.createObjectURL(file) : "";
}

function onPhoneInput(event) {
  const cleanValue = String(event?.target?.value || "").replace(/[^0-9+\-()\s]/g, "");
  profile.value.phone = cleanValue;
}

function validateProfileForm() {
  const fullName = String(profile.value.fullName || "").trim();
  const email = String(profile.value.email || "").trim();
  const phone = String(profile.value.phone || "").trim();

  if (!fullName) {
    return "Full name is required.";
  }

  if (fullName.length > 150) {
    return "Full name must be at most 150 characters.";
  }

  if (email && !EMAIL_PATTERN.test(email)) {
    return "Email must be valid.";
  }

  if (email.length > 150) {
    return "Email must be at most 150 characters.";
  }

  if (phone && !PHONE_ALLOWED_PATTERN.test(phone)) {
    return "Phone can contain only numbers, spaces, +, -, and brackets.";
  }

  if (phone && (phone.match(/\d/g) || []).length < 10) {
    return "Phone must contain at least 10 digits.";
  }

  if (phone.length > 30) {
    return "Phone must be at most 30 characters.";
  }

  return "";
}

async function uploadPicture() {
  if (!selectedPic.value) return;

  const file = selectedPic.value;
  const allowed = ["image/jpeg", "image/jpg", "image/png", "image/webp"];
  if (!allowed.includes(String(file.type || "").toLowerCase())) {
    error("Only JPG, PNG, or WEBP images are allowed.");
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    error("Image must be 5MB or smaller.");
    return;
  }

  savingPic.value = true;
  try {
    const me = await uploadMyProfilePicture(file);
    selectedPic.value = null;
    clearSelectedPicPreview();
    if (fileInput.value) fileInput.value.value = "";

    profile.value.hasProfilePicture = !!me.hasProfilePicture;
    profile.value.profilePictureUpdatedAt = me.profilePictureUpdatedAt || Date.now();
    avatarBroken.value = false;
    avatarVersion.value = String(Date.now());

    applySessionUser(me);
    await refreshAvatarUrl();
    success("Profile picture updated.");
  } catch (e) {
    error(e?.message || "Failed to upload profile picture.");
  } finally {
    savingPic.value = false;
  }
}

async function saveProfile() {
  const validationError = validateProfileForm();
  if (validationError) {
    error(validationError);
    return;
  }

  savingProfile.value = true;
  try {
    const me = await updateMe({
      fullName: String(profile.value.fullName || "").trim(),
      email: String(profile.value.email || "").trim(),
      phone: String(profile.value.phone || "").trim(),
    });
    profile.value = {
      ...profile.value,
      fullName: me.fullName || "",
      email: me.email || "",
      phone: me.phone || "",
      department: me.department || "",
      hasProfilePicture: !!me.hasProfilePicture,
      profilePictureUpdatedAt: me.profilePictureUpdatedAt || profile.value.profilePictureUpdatedAt,
    };
    applySessionUser(me);
    success("Profile updated successfully.");
  } catch (e) {
    error(e?.message || "Failed to update profile.");
  } finally {
    savingProfile.value = false;
  }
}

async function savePassword() {
  const currentPassword = String(password.value.currentPassword || "");
  const newPassword = String(password.value.newPassword || "");
  const confirmPassword = String(password.value.confirmPassword || "");

  if (!currentPassword || !newPassword || !confirmPassword) {
    error("Fill all password fields.");
    return;
  }

  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(newPassword)) {
    error("Password must be at least 8 characters with letters and numbers.");
    return;
  }

  if (newPassword !== confirmPassword) {
    error("New password and confirm password must match.");
    return;
  }

  if (currentPassword === newPassword) {
    error("New password must be different from current password.");
    return;
  }

  savingPassword.value = true;
  try {
    await changeMyPassword({
      currentPassword,
      newPassword,
      confirmPassword,
    });
    resetPasswordForm();
    success("Password changed successfully.");
  } catch (e) {
    error(e?.message || "Failed to change password.");
  } finally {
    savingPassword.value = false;
  }
}

onUnmounted(() => {
  // Release the browser object URL if the user leaves after selecting an image.
  clearSelectedPicPreview();
});

load();
</script>

<style scoped>
.profileShell {
  --ink: #111827;
  --muted: #6b7280;
  --line: #e5e7eb;
  --panel: #ffffff;
  --brand: #2563eb;
  --brandDark: #1d4ed8;
  --navy: #1f2937;
  background:
    radial-gradient(90% 70% at 0% 0%, rgba(37, 99, 235, 0.08) 0%, rgba(37, 99, 235, 0) 70%),
    radial-gradient(70% 60% at 100% 0%, rgba(31, 41, 55, 0.06) 0%, rgba(31, 41, 55, 0) 70%);
  border-radius: 14px;
  padding: 10px;
  color: var(--ink);
}

.profileHero {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-height: 170px;
  margin-bottom: 18px;
  padding: 24px;
  border-radius: 14px;
  color: #fff;
  background:
    radial-gradient(85% 120% at 100% 0%, rgba(37, 99, 235, 0.22), transparent 60%),
    linear-gradient(135deg, #1f2937 0%, #111827 100%);
  box-shadow: 0 6px 18px rgba(17, 24, 39, 0.08);
}

.heroGlow {
  position: absolute;
  inset: auto -60px -90px auto;
  width: 260px;
  height: 260px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.13);
}

.heroIdentity,
.heroMeta,
.sectionHead,
.panelSplit,
.picActions,
.btnRow,
.helperNote,
.passwordRule {
  position: relative;
  display: flex;
  align-items: center;
}

.heroIdentity {
  gap: 18px;
}

.heroAvatar {
  flex: 0 0 auto;
  width: 112px;
  height: 112px;
  display: grid;
  place-items: center;
  border-radius: 32px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.22), rgba(255, 255, 255, 0.08));
  border: 1px solid rgba(255, 255, 255, 0.28);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.22), 0 18px 36px rgba(0, 0, 0, 0.2);
  color: #fff;
  font-size: 34px;
  font-weight: 900;
  letter-spacing: 0.04em;
  overflow: hidden;
}

.heroAvatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.eyebrow {
  display: inline-flex;
  margin-bottom: 7px;
  color: #1e3a8a;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.profileHero .eyebrow {
  color: #bfdbfe;
}

h2,
h3,
p,
dl {
  margin: 0;
}

h2 {
  font-size: clamp(28px, 4vw, 42px);
  line-height: 1.02;
  letter-spacing: -0.04em;
}

h3 {
  color: #0f172a;
  font-size: 18px;
  letter-spacing: -0.01em;
}

.pageSub {
  max-width: 640px;
  margin-top: 8px;
  color: rgba(255, 255, 255, 0.78);
  font-size: 14px;
}

.heroMeta {
  align-self: flex-start;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  max-width: 330px;
}

.heroMeta span:not(.statusDot),
.heroMeta strong,
.safeBadge,
.lockBadge {
  border-radius: 999px;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 800;
}

.heroMeta span:not(.statusDot),
.heroMeta strong {
  color: #f9fafb;
  background: rgba(255, 255, 255, 0.11);
  border: 1px solid rgba(255, 255, 255, 0.16);
}

.statusDot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #22c55e;
  box-shadow: 0 0 0 5px rgba(34, 197, 94, 0.18);
}

.profileGrid {
  display: grid;
  grid-template-columns: minmax(270px, 0.34fr) minmax(0, 1fr);
  gap: 18px;
}

.profileSide,
.profileMain {
  display: grid;
  gap: 18px;
  align-content: start;
}

.settingsPanel {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 6px 18px rgba(17, 24, 39, 0.05);
}

.photoPanel {
  background:
    linear-gradient(180deg, #ffffff 0%, #f9fafb 100%),
    #fff;
}

.securityPanel {
  background:
    linear-gradient(180deg, #ffffff 0%, #f9fafb 100%),
    #fff;
}

.sectionHead {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.sectionSub {
  margin-top: 5px;
  color: var(--muted);
  font-size: 13px;
}

.panelSplit {
  align-items: flex-start;
}

.safeBadge {
  color: #1e3a8a;
  background: #dbeafe;
  border: 1px solid #bfdbfe;
}

.lockBadge {
  color: #1d4ed8;
  background: #dbeafe;
  border: 1px solid #bfdbfe;
}

.photoPreview {
  display: grid;
  justify-items: center;
  gap: 12px;
  margin: 4px 0 16px;
  text-align: center;
}

.photoRing {
  padding: 8px;
  border-radius: 999px;
  background: conic-gradient(from 160deg, #2563eb, #1f2937, #cbd5e1, #2563eb);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.12);
}

.avatar {
  display: grid;
  width: 136px;
  height: 136px;
  place-items: center;
  border: 5px solid #fff;
  border-radius: 999px;
  object-fit: cover;
  background: linear-gradient(135deg, #dbeafe, #f3f4f6);
}

.avatar.fallback {
  color: #0f172a;
  font-size: 34px;
  font-weight: 900;
}

.photoHint,
.selectedFile,
.passwordRule p {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
}

.hidden {
  display: none;
}

.picActions {
  flex-wrap: wrap;
  gap: 10px;
}

.selectedFile {
  margin-top: 10px;
  padding: 9px 11px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  word-break: break-word;
}

.selectedFile.muted {
  color: #94a3b8;
}

.selectedImagePreview {
  display: grid;
  justify-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 10px;
  border: 1px solid #dbe5f0;
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.previewLabel {
  color: #475569;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.selectedImagePreview img {
  width: 104px;
  height: 104px;
  border: 4px solid #ffffff;
  border-radius: 999px;
  object-fit: cover;
  background: #f1f5f9;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.12);
}

.accountList {
  display: grid;
  gap: 10px;
}

.accountList div {
  display: grid;
  gap: 4px;
  padding: 12px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.accountList dt {
  color: #64748b;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.accountList dd {
  margin: 0;
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
}

.formGrid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.passwordGrid {
  grid-template-columns: 1fr;
}

.formRow {
  display: grid;
  gap: 7px;
}

.formRow span {
  color: #334155;
  font-size: 12px;
  font-weight: 900;
}

.input {
  width: 100%;
  height: 44px;
  border: 1px solid #d7e0ea;
  border-radius: 13px;
  background: #fff;
  color: #0f172a;
  padding: 0 13px;
  font: inherit;
  box-sizing: border-box;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
}

.input:focus {
  border-color: #9ca3af;
  box-shadow: 0 0 0 3px rgba(229, 231, 235, 0.9);
  outline: none;
}

.passwordField {
  position: relative;
}

.passwordInput {
  padding-right: 46px;
}

.passwordToggle {
  position: absolute;
  top: 50%;
  right: 7px;
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transform: translateY(-50%);
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
}

.passwordToggle:hover {
  background: #f1f5f9;
  color: #1d4ed8;
}

.passwordToggle:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 2px;
}

.passwordIcon {
  width: 18px;
  height: 18px;
}

.inputReadonly {
  color: #64748b;
  background: linear-gradient(180deg, #f8fafc, #eef2f7);
  cursor: not-allowed;
}

.helperNote {
  gap: 8px;
  margin-top: 14px;
  color: #64748b;
  font-size: 12px;
}

.passwordRule {
  gap: 10px;
  margin-top: 14px;
  padding: 12px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.passwordRule span {
  flex: 0 0 auto;
  width: 9px;
  height: 38px;
  border-radius: 999px;
  background: linear-gradient(180deg, #2563eb, #1f2937);
}

.btnRow {
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.btn {
  min-height: 42px;
  padding: 10px 15px;
  border: 1px solid #cbd5e1;
  border-radius: 13px;
  background: #fff;
  color: #0f172a;
  cursor: pointer;
  font-weight: 900;
  transition: transform 0.12s ease, box-shadow 0.15s ease, border-color 0.15s ease;
}

.btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.12);
}

.btn-primary {
  border-color: var(--brand);
  background: var(--brand);
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: var(--brandDark);
}

.btn-ghost {
  background: #f8fafc;
}

.btn:disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

@media (max-width: 1060px) {
  .profileGrid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .profileShell {
    padding: 12px;
    border-radius: 18px;
  }

  .profileHero {
    align-items: flex-start;
    flex-direction: column;
    padding: 18px;
  }

  .heroIdentity {
    align-items: flex-start;
    flex-direction: column;
  }

  .heroAvatar {
    width: 92px;
    height: 92px;
    border-radius: 26px;
    font-size: 28px;
  }

  .heroMeta {
    justify-content: flex-start;
  }

  .settingsPanel {
    padding: 15px;
    border-radius: 17px;
  }

  .formGrid {
    grid-template-columns: 1fr;
  }

  .panelSplit,
  .sectionHead {
    align-items: flex-start;
    flex-direction: column;
  }

  .btnRow,
  .picActions {
    justify-content: stretch;
  }

  .btn {
    flex: 1;
  }
}
</style>
