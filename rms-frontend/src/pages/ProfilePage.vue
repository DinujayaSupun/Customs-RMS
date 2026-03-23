<template>
  <AppLayout>
    <div class="profileCanvas">
      <div class="pageHead">
        <div>
          <h2>My Profile</h2>
          <p class="pageSub">Manage your personal details, account image, and sign-in security from one place.</p>
        </div>
        <span class="headBadge">Account Center</span>
      </div>

      <div class="grid">
      <section class="card cardHighlight">
        <div class="sectionHead">
          <h3>Profile Picture</h3>
          <p class="sectionSub">This image appears in the top header after sign in.</p>
        </div>
        <div class="picRow">
          <img
            v-if="avatarUrl && !avatarBroken"
            :src="avatarUrl"
            class="avatar"
            alt="Profile picture"
            @error="avatarBroken = true"
          />
          <div v-else class="avatar fallback">{{ initials }}</div>

          <div class="picActions">
            <input ref="fileInput" type="file" accept="image/png,image/jpeg,image/webp" class="hidden" @change="onPick" />
            <button class="btn btn-ghost" @click="pickFile" :disabled="savingPic">Choose Image</button>
            <button class="btn btn-primary" :disabled="savingPic || !selectedPic" @click="uploadPicture">
              {{ savingPic ? "Uploading..." : "Upload" }}
            </button>
            <HoverHint text="Allowed formats: JPG, PNG, WEBP. Max size: 5MB." />
          </div>
        </div>

        <div class="metaPills">
          <span class="pill"><b>Username:</b> {{ profile.username || "-" }}</span>
          <span class="pill"><b>Role:</b> {{ profile.role || "-" }}</span>
        </div>
      </section>

      <section class="card">
        <div class="sectionHead">
          <h3>Basic Details</h3>
          <p class="sectionSub">Keep your contact details up to date for internal communication.</p>
        </div>
        <div class="formRow">
          <label>Username</label>
          <input
            class="input inputReadonly"
            :value="profile.username"
            disabled
            title="Username cannot be changed."
          />
        </div>
        <div class="formRow">
          <label>Role</label>
          <input
            class="input inputReadonly"
            :value="profile.role"
            disabled
            title="Role can only be changed by ADMIN."
          />
        </div>
        <div class="formRow">
          <label>Full Name</label>
          <input class="input" v-model="profile.fullName" />
        </div>
        <div class="formRow">
          <label>Email</label>
          <input class="input" v-model="profile.email" type="email" />
        </div>
        <div class="formRow">
          <label>Phone</label>
          <input class="input" v-model="profile.phone" />
        </div>
        <div class="formRow">
          <label>Department</label>
          <input
            class="input inputReadonly"
            :value="profile.department"
            disabled
            title="Department can only be changed by ADMIN."
          />
        </div>
        <div class="hintInline">
          <span class="hintLabel">Department updates</span>
          <HoverHint text="Department can only be changed by ADMIN in user management." />
        </div>

        <div class="btnRow">
          <button class="btn btn-ghost" :disabled="savingProfile" @click="load">Reset</button>
          <button class="btn btn-primary" :disabled="savingProfile" @click="saveProfile">
            {{ savingProfile ? "Saving..." : "Save Profile" }}
          </button>
        </div>
      </section>

      <section class="card cardWide">
        <div class="sectionHead">
          <h3>Change Password</h3>
          <p class="sectionSub">Use a strong password. It must include letters and numbers with at least 8 characters.</p>
        </div>
        <div class="formRow">
          <label>Current Password</label>
          <input class="input" v-model="password.currentPassword" type="password" />
        </div>
        <div class="formRow">
          <label>New Password</label>
          <input class="input" v-model="password.newPassword" type="password" placeholder="At least 8 chars with letters and numbers" />
        </div>
        <div class="formRow">
          <label>Confirm New Password</label>
          <input class="input" v-model="password.confirmPassword" type="password" />
        </div>

        <div class="btnRow">
          <button class="btn btn-primary" :disabled="savingPassword" @click="savePassword">
            {{ savingPassword ? "Updating..." : "Update Password" }}
          </button>
        </div>
      </section>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, ref } from "vue";
import AppLayout from "../layouts/AppLayout.vue";
import HoverHint from "../components/HoverHint.vue";
import { useToast } from "../composables/useToast";
import {
  buildMyProfilePictureUrl,
  changeMyPassword,
  getMe,
  updateMe,
  uploadMyProfilePicture,
} from "../api/auth.api";
import { getCurrentUser, updateSessionUser } from "../auth/currentUser";

const { success, error } = useToast();

const fileInput = ref(null);
const selectedPic = ref(null);
const savingPic = ref(false);
const savingProfile = ref(false);
const savingPassword = ref(false);
const avatarBroken = ref(false);
const avatarVersion = ref("");

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

const initials = computed(() => {
  const text = String(profile.value.fullName || profile.value.username || "U").trim();
  if (!text) return "U";
  const parts = text.split(/\s+/).slice(0, 2);
  return parts.map((p) => p[0]?.toUpperCase() || "").join("") || "U";
});

const avatarUrl = computed(() => {
  if (!profile.value.hasProfilePicture) return "";
  return buildMyProfilePictureUrl(avatarVersion.value || profile.value.profilePictureUpdatedAt || Date.now());
});

function resetPasswordForm() {
  password.value = {
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  };
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
  selectedPic.value = file;
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
    if (fileInput.value) fileInput.value.value = "";

    profile.value.hasProfilePicture = !!me.hasProfilePicture;
    profile.value.profilePictureUpdatedAt = me.profilePictureUpdatedAt || Date.now();
    avatarBroken.value = false;
    avatarVersion.value = String(Date.now());

    applySessionUser(me);
    success("Profile picture updated.");
  } catch (e) {
    error(e?.message || "Failed to upload profile picture.");
  } finally {
    savingPic.value = false;
  }
}

async function saveProfile() {
  const phone = String(profile.value.phone || "").trim();
  const digitCount = (phone.match(/\d/g) || []).length;
  if (phone && digitCount < 10) {
    error("Phone must contain at least 10 digits.");
    return;
  }

  savingProfile.value = true;
  try {
    const me = await updateMe({
      fullName: profile.value.fullName,
      email: profile.value.email,
      phone: profile.value.phone,
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
  if (!password.value.currentPassword || !password.value.newPassword || !password.value.confirmPassword) {
    error("Fill all password fields.");
    return;
  }

  if (password.value.newPassword !== password.value.confirmPassword) {
    error("New password and confirm password must match.");
    return;
  }

  savingPassword.value = true;
  try {
    await changeMyPassword({
      currentPassword: password.value.currentPassword,
      newPassword: password.value.newPassword,
      confirmPassword: password.value.confirmPassword,
    });
    resetPasswordForm();
    success("Password changed successfully.");
  } catch (e) {
    error(e?.message || "Failed to change password.");
  } finally {
    savingPassword.value = false;
  }
}

load();
</script>

<style scoped>
.profileCanvas {
  background:
    radial-gradient(70% 60% at 0% 0%, rgba(37, 99, 235, 0.08) 0%, rgba(37, 99, 235, 0) 80%),
    radial-gradient(60% 55% at 100% 0%, rgba(22, 163, 74, 0.08) 0%, rgba(22, 163, 74, 0) 80%);
  border-radius: 16px;
  padding: 14px;
}

.pageHead {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
}

h2 {
  margin: 0;
  font-size: 26px;
  letter-spacing: 0.01em;
}

.pageSub {
  margin: 4px 0 0;
  color: #475569;
  font-size: 13px;
  max-width: 700px;
}

.headBadge {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #1e3a8a;
  background: #dbeafe;
  border: 1px solid #bfdbfe;
  padding: 6px 10px;
  border-radius: 999px;
}

.grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.95fr) minmax(320px, 1.05fr);
  gap: 14px;
}

.card {
  background: linear-gradient(180deg, #ffffff 0%, #fbfcff 100%);
  border: 1px solid #dbe1ea;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.06);
}

.cardHighlight {
  border-color: #bfdbfe;
  box-shadow: 0 10px 28px rgba(37, 99, 235, 0.12);
}

.cardWide {
  grid-column: 1 / -1;
}

h3 {
  margin: 0;
  font-size: 17px;
}

.sectionHead {
  margin-bottom: 12px;
}

.sectionSub {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
}

.picRow {
  display: flex;
  gap: 14px;
  align-items: center;
}

.avatar {
  width: 82px;
  height: 82px;
  border-radius: 999px;
  object-fit: cover;
  border: 3px solid #cbd5e1;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.12);
}

.avatar.fallback {
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #e2e8f0, #cbd5e1);
  color: #0f172a;
  font-weight: 700;
  font-size: 22px;
}

.picActions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.hidden {
  display: none;
}

.formRow {
  display: grid;
  gap: 6px;
  margin-bottom: 11px;
}

label {
  font-size: 12px;
  font-weight: 700;
  color: #334155;
}

.input {
  height: 40px;
  border-radius: 10px;
  border: 1px solid #dbe1ea;
  background: #ffffff;
  padding: 0 12px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.input:focus {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.14);
  outline: none;
}

.inputReadonly {
  background: #f1f5f9;
  color: #6b7280;
  border-color: #d6dde8;
  cursor: not-allowed;
}

.btnRow {
  display: flex;
  gap: 8px;
  margin-top: 6px;
}

.btn {
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid #dbe1ea;
  background: #fff;
  cursor: pointer;
  font-weight: 600;
  transition: transform 0.1s ease, box-shadow 0.15s ease;
}

.btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 14px rgba(15, 23, 42, 0.1);
}

.btn-primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.btn-ghost {
  background: #f8fafc;
  color: #0f172a;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.metaPills {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pill {
  font-size: 12px;
  color: #334155;
  background: #f8fafc;
  border: 1px solid #dbe1ea;
  border-radius: 999px;
  padding: 6px 10px;
}

.smallHint {
  color: #6b7280;
  font-size: 12px;
}

.hintInline {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.hintLabel {
  color: #6b7280;
  font-size: 12px;
}

@media (max-width: 980px) {
  .grid {
    grid-template-columns: 1fr;
  }

  .cardWide {
    grid-column: auto;
  }

  .pageHead {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
