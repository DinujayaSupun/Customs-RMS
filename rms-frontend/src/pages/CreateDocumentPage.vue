<template>
  <AppLayout>
    <div class="pageHead">
      <h2>Create Document</h2>
    </div>

    <div v-if="!canCreate" class="errorBox">
      You are not allowed to create documents with your current role permissions.
    </div>

    <div v-else class="card">
      <div class="formGrid">
        <div>
          <div class="labelTop">Reference No</div>
          <input v-model="refNo" class="input" placeholder="DOC-001" />
        </div>

        <div>
          <div class="labelTop">Received Date</div>
          <input v-model="receivedDate" type="date" class="input" />
        </div>

        <div class="span2">
          <div class="labelTop">Title</div>
          <input v-model="title" class="input" placeholder="Document title" />
        </div>

        <div class="span2">
          <div class="labelTop">Company Name</div>
          <input v-model="companyName" class="input" placeholder="Company name" />
        </div>

        <div>
          <div class="labelTop">Priority</div>
          <select v-model="priority" class="input">
            <option>LOW</option>
            <option>MEDIUM</option>
            <option>HIGH</option>
            <option>URGENT</option>
          </select>
        </div>

        <!-- Main file upload -->
        <div class="span2">
          <div class="labelTop">Document Files (optional)</div>
          <input
            id="createDocumentFileInput"
            ref="fileInputRef"
            class="hiddenFileInput"
            type="file"
            multiple
            @change="onFileChange"
          />
          <div class="filePickRow">
            <button class="btn btn-sm" type="button" @click="openFilePicker">Choose Files</button>
            <span class="filePickLabel">{{ selectedFiles.length ? `${selectedFiles.length} file(s) selected` : "No files chosen" }}</span>
            <HoverHint text="PDF and image files can be previewed here. DOC/DOCX and other file types cannot be previewed in-browser." />
          </div>
          <div class="small">
            First selected file becomes the Main file. Later files become attachments.
          </div>

          <div v-if="selectedFiles.length" class="previewBox">
            <div
              v-for="(file, index) in selectedFiles"
              :key="getFileKey(file)"
              class="selectedFileRow"
            >
              <div class="fileName">
                <b>{{ file.name }}</b>
                <span class="tag">{{ getSelectedFileRole(index) }}</span>
                <div class="small">{{ file.type || "unknown type" }}</div>
              </div>
              <div class="selectedFileActions">
                <button class="btn btn-sm" type="button" @click="openLocalPreview(file)">View</button>
                <button class="btn btn-sm danger" type="button" @click="removeFile(file)">Remove</button>
              </div>
            </div>

            <!-- Small preview -->
            <div v-if="previewFile" class="previewBody">
              <iframe
                v-if="isPdf"
                :src="localUrl"
                class="frame"
                title="PDF preview"
              ></iframe>

              <img v-else-if="isImage" :src="localUrl" class="img" alt="preview" />

              <div v-else class="noPreview">
                Preview not available for this file type. Use the <b>View</b> button.
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="error" class="errorBox" style="margin-top:12px;">{{ error }}</div>
      <div v-if="success" class="successBox" style="margin-top:12px;">{{ success }}</div>

      <div class="actions">
        <button class="btn btn-primary" :disabled="busy" @click="submit">
          {{ busy ? "Creating..." : "Create" }}
        </button>
        <button class="btn" :disabled="busy" @click="goBack">Cancel</button>
      </div>
    </div>

    <!-- Local preview modal (for selected file before upload) -->
    <div v-if="previewOpen" class="overlay" @click.self="previewOpen=false">
      <div class="modal">
        <div class="modalHead">
          <div class="modalTitleBlock">
            <div class="modalTitle">Preview</div>
            <div class="modalSub">{{ previewFile?.name }}</div>
          </div>
          <button class="iconBtn" @click="previewOpen=false">x</button>
        </div>

        <div class="modalBody">
          <iframe v-if="isPdf" :src="localUrl" class="bigFrame"></iframe>
          <img v-else-if="isImage" :src="localUrl" class="bigImg" />
          <div v-else class="noPreview">
            This file type cannot be previewed in browser. After upload, user can download it from attachments.
          </div>
        </div>

        <div class="modalFoot">
          <button class="btn" @click="previewOpen=false">Close</button>
        </div>
      </div>
    </div>

  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import AppLayout from "../layouts/AppLayout.vue";
import HoverHint from "../components/HoverHint.vue";
import { createDocument, uploadAttachment } from "../api/documents.api";
import { getCurrentUser, hasPermission } from "../auth/currentUser";
import {
  addSelectedFiles,
  getFileKey,
  getSelectedFileRole,
  removeSelectedFile,
  uploadFilesInSelectedOrder,
} from "../utils/createDocumentFilesLogic";

const router = useRouter();

const user = ref(getCurrentUser());
const canCreate = computed(() => hasPermission(user.value, "CREATE_DOCUMENT"));

function refreshCurrentUser() {
  user.value = getCurrentUser();
}

const refNo = ref("");
const title = ref("");
const companyName = ref("");
const receivedDate = ref("");
const priority = ref("MEDIUM");

const busy = ref(false);
const error = ref("");
const success = ref("");

// File handling
const selectedFiles = ref([]);
const previewFile = ref(null);
const localUrl = ref("");
const previewOpen = ref(false);
const fileInputRef = ref(null);

const isPdf = computed(() => previewFile.value && previewFile.value.type === "application/pdf");
const isImage = computed(() => previewFile.value && previewFile.value.type.startsWith("image/"));

function onFileChange(e) {
  selectedFiles.value = addSelectedFiles(selectedFiles.value, e.target.files);
  if (!previewFile.value && selectedFiles.value[0]) {
    setPreviewFile(selectedFiles.value[0]);
  }
  if (fileInputRef.value) fileInputRef.value.value = "";
}

function openFilePicker() {
  fileInputRef.value?.click();
}

function openLocalPreview(file) {
  if (!file) return;
  setPreviewFile(file);
  previewOpen.value = true;
}

function setPreviewFile(file) {
  if (localUrl.value) URL.revokeObjectURL(localUrl.value);
  previewFile.value = file;
  localUrl.value = file ? URL.createObjectURL(file) : "";
}

function removeFile(file) {
  selectedFiles.value = removeSelectedFile(selectedFiles.value, file);
  if (previewFile.value && getFileKey(previewFile.value) === getFileKey(file)) {
    previewOpen.value = false;
    setPreviewFile(selectedFiles.value[0] || null);
  } else if (!selectedFiles.value.length) {
    setPreviewFile(null);
  }
}

onUnmounted(() => {
  window.removeEventListener("rms_auth_changed", refreshCurrentUser);
  window.removeEventListener("rms_permissions_updated", refreshCurrentUser);
  if (localUrl.value) URL.revokeObjectURL(localUrl.value);
});

onMounted(() => {
  window.addEventListener("rms_auth_changed", refreshCurrentUser);
  window.addEventListener("rms_permissions_updated", refreshCurrentUser);
});

function validate() {
  if (!refNo.value.trim()) return "Reference No is required.";
  if (!title.value.trim()) return "Title is required.";
  if (!companyName.value.trim()) return "Company name is required.";
  if (!receivedDate.value) return "Received date is required.";
  return "";
}

async function submit() {
  error.value = "";
  success.value = "";
  const v = validate();
  if (v) return (error.value = v);

  busy.value = true;
  try {
    const payload = {
      refNo: refNo.value.trim(),
      title: title.value.trim(),
      companyName: companyName.value.trim(),
      receivedDate: receivedDate.value,
      priority: priority.value,
    };

    const created = await createDocument(payload);

    // Upload in selected order: first file becomes v1/main, later files become additional attachments.
    await uploadFilesInSelectedOrder(created.id, selectedFiles.value, uploadAttachment);

    success.value = "Document created successfully.";
    router.push(`/documents/${created.id}`);
  } catch (e) {
    error.value = e?.message ?? "Create failed";
  } finally {
    busy.value = false;
  }
}

function goBack() {
  router.push("/documents");
}
</script>

<style scoped>
.pageHead { display:flex; align-items:center; justify-content:space-between; margin-bottom:14px; }
h2 { margin:0; }
.card { background:#fff; padding:16px; border-radius:10px; }

.formGrid {
  display:grid;
  grid-template-columns: 1fr 1fr;
  gap:12px;
}
.span2 { grid-column: span 2; }

.labelTop { font-size:12px; font-weight:800; color:#374151; margin-bottom:6px; }
.hiddenFileInput { display:none; }
.filePickRow { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
.filePickLabel {
  font-size:13px;
  color:#374151;
  max-width:340px;
  white-space:nowrap;
  overflow:hidden;
  text-overflow:ellipsis;
}
.input {
  height:38px; border-radius:8px; border:1px solid #e5e7eb; padding:0 10px; outline:none; width:100%;
}
.input:focus { border-color:#9ca3af; }

.actions { margin-top:14px; display:flex; gap:10px; justify-content:flex-end; }

.btn { padding:10px 12px; border-radius:8px; border:1px solid #e5e7eb; background:#fff; cursor:pointer; }
.btn:hover { background:#f9fafb; }
.btn-primary { background:#2563eb; border-color:#2563eb; color:#fff; }
.btn-primary:hover { background:#1d4ed8; }
.btn:disabled { opacity:0.6; cursor:not-allowed; }
.btn-sm { padding:7px 10px; font-size:12px; }

.errorBox {
  background:#fef2f2;
  border:1px solid #fecaca;
  color:#991b1b;
  padding:10px 12px;
  border-radius:8px;
}
.successBox {
  background:#ecfdf5;
  border:1px solid #a7f3d0;
  color:#065f46;
  padding:10px 12px;
  border-radius:8px;
}

.previewBox {
  margin-top:10px;
  border:1px solid #e5e7eb;
  border-radius:10px;
  padding:10px;
  background:#f9fafb;
}
.previewHead {
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:10px;
}
.selectedFileRow {
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:12px;
  padding:10px;
  border:1px solid #e5e7eb;
  border-radius:8px;
  background:#fff;
}
.selectedFileRow + .selectedFileRow { margin-top:8px; }
.selectedFileActions { display:flex; gap:8px; flex-wrap:wrap; justify-content:flex-end; }
.fileName { min-width:0; }
.small { font-size:12px; color:#6b7280; }
.tag {
  display:inline-flex;
  align-items:center;
  margin-left:8px;
  padding:2px 6px;
  border-radius:999px;
  background:#dbeafe;
  color:#1d4ed8;
  font-size:11px;
  font-weight:800;
}
.danger { color:#991b1b; border-color:#fecaca; }
.danger:hover { background:#fef2f2; }

.previewBody { margin-top:10px; }
.frame { width:100%; height:220px; border:1px solid #e5e7eb; border-radius:8px; background:#fff; }
.img { max-width:100%; max-height:220px; border-radius:8px; border:1px solid #e5e7eb; background:#fff; }
.noPreview { font-size:13px; color:#6b7280; padding:10px; background:#fff; border:1px solid #e5e7eb; border-radius:8px; }

/* Modal */
.overlay {
  position:fixed; inset:0;
  background:rgba(0,0,0,0.4);
  display:flex; align-items:center; justify-content:center;
  padding:14px;
}
.modal {
  width:100%;
  max-width:900px;
  min-width:0;
  box-sizing:border-box;
  background:#fff;
  border-radius:10px;
  overflow:hidden;
}
.modalHead {
  display:flex; align-items:flex-start; justify-content:space-between;
  gap:12px;
  min-width:0;
  padding:14px 16px;
  border-bottom:1px solid #eee;
}
.modalTitleBlock {
  flex:1 1 auto;
  min-width:0;
  max-width:100%;
}
.modalTitle { font-weight:800; font-size:14px; }
.modalSub {
  max-width:100%;
  font-size:12px;
  color:#6b7280;
  margin-top:2px;
  overflow-wrap:anywhere;
  word-break:break-word;
}
.modalBody { padding:16px; }
.modalFoot {
  display:flex; justify-content:flex-end; gap:10px;
  padding:14px 16px;
  border-top:1px solid #eee;
}
.iconBtn {
  height:32px; width:36px; border-radius:8px; border:1px solid #e5e7eb; background:#fff; cursor:pointer;
}
.iconBtn:hover { background:#f9fafb; }

.bigFrame { width:100%; height:70vh; border:1px solid #e5e7eb; border-radius:8px; background:#fff; }
.bigImg { max-width:100%; max-height:70vh; display:block; margin:0 auto; border-radius:8px; border:1px solid #e5e7eb; background:#fff; }
</style>
