<template>
  <AppLayout>
    <div class="pageHead">
      <h2>Create Document</h2>
    </div>

    <div v-if="!canCreate" class="errorBox">
      You are not allowed to create documents. Switch to <b>DC</b> or <b>PMA</b>.
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

        <div>
          <div class="labelTop">Initial Owner</div>
          <select v-model.number="initialOwner" class="input">
            <option v-for="u in ownerOptions" :key="u.id" :value="u.id">
              {{ u.role }} (id={{ u.id }})
            </option>
          </select>
          <div class="hint">If PMA creates, initial owner will be forced to DC.</div>
        </div>

        <!-- Main file upload -->
        <div class="span2">
          <div class="labelTop">Main Document File (optional)</div>
          <input type="file" @change="onFileChange" />
          <div class="hint">PDF/images will preview here. DOC/DOCX will show as a link.</div>

          <div v-if="selectedFile" class="previewBox">
            <div class="previewHead">
              <div class="fileName">
                <b>{{ selectedFile.name }}</b>
                <div class="small">{{ prettyType }}</div>
              </div>
              <button class="btn btn-sm" type="button" @click="openLocalPreview">View</button>
            </div>

            <!-- Small preview -->
            <div class="previewBody">
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
          <div>
            <div class="modalTitle">Preview</div>
            <div class="modalSub">{{ selectedFile?.name }}</div>
          </div>
          <button class="iconBtn" @click="previewOpen=false">✕</button>
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
import { ref, computed, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import AppLayout from "../layouts/AppLayout.vue";
import { createDocument, uploadAttachment } from "../api/documents.api";
import { listUsers } from "../api/auth.api";
import { getCurrentUser } from "../auth/currentUser";

const router = useRouter();

const user = ref(getCurrentUser());
const canCreate = computed(() => ["DC", "PMA"].includes(user.value?.role));
const users = ref([]);

const refNo = ref("");
const title = ref("");
const companyName = ref("");
const receivedDate = ref("");
const priority = ref("MEDIUM");
const initialOwner = ref(null);

const busy = ref(false);
const error = ref("");
const success = ref("");

// Owners dropdown = non-PMA roles (DC/DDC/SC/ASC)
const ownerOptions = computed(() => users.value.filter(u => u.role !== "PMA"));

// default owner option
if (ownerOptions.value.length > 0) initialOwner.value = ownerOptions.value[0].id;

(async () => {
  try {
    users.value = await listUsers();
    if (ownerOptions.value.length > 0 && !initialOwner.value) {
      initialOwner.value = ownerOptions.value[0].id;
    }
  } catch {
    users.value = [];
  }
})();

// File handling
const selectedFile = ref(null);
const localUrl = ref("");
const previewOpen = ref(false);

const isPdf = computed(() => selectedFile.value && selectedFile.value.type === "application/pdf");
const isImage = computed(() => selectedFile.value && selectedFile.value.type.startsWith("image/"));
const prettyType = computed(() => selectedFile.value?.type || "unknown type");

function onFileChange(e) {
  const f = e.target.files?.[0] ?? null;
  selectedFile.value = f;

  if (localUrl.value) URL.revokeObjectURL(localUrl.value);
  localUrl.value = f ? URL.createObjectURL(f) : "";
}

function openLocalPreview() {
  if (!selectedFile.value) return;
  previewOpen.value = true;
}

onUnmounted(() => {
  if (localUrl.value) URL.revokeObjectURL(localUrl.value);
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
    // PMA creates on behalf of DC => initial owner forced to DC
    const dcUser = users.value.find(u => u.role === "DC");
    const owner = user.value.role === "PMA" ? dcUser?.id : Number(initialOwner.value);

    const payload = {
      refNo: refNo.value.trim(),
      title: title.value.trim(),
      companyName: companyName.value.trim(),
      receivedDate: receivedDate.value,
      priority: priority.value,
      currentOwnerUserId: owner,
    };

    const created = await createDocument(payload);

    // If a file selected, upload it as v1 attachment
    if (selectedFile.value) {
      await uploadAttachment(created.id, selectedFile.value);
    }

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
.hint { margin-top:6px; font-size:12px; color:#6b7280; }

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
.fileName { min-width:0; }
.small { font-size:12px; color:#6b7280; }

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
  background:#fff;
  border-radius:10px;
  overflow:hidden;
}
.modalHead {
  display:flex; align-items:flex-start; justify-content:space-between;
  padding:14px 16px;
  border-bottom:1px solid #eee;
}
.modalTitle { font-weight:800; font-size:14px; }
.modalSub { font-size:12px; color:#6b7280; margin-top:2px; }
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