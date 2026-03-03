<template>
  <AppLayout>
    <div class="pageHead">
      <h2>My Inbox</h2>
      <button class="btn" @click="load">Refresh</button>
    </div>

    <div v-if="error" class="errorBox" style="margin-bottom:12px;">{{ error }}</div>

    <div class="card">
      <table class="table">
        <thead>
          <tr>
            <th>Ref No</th>
            <th>Title</th>
            <th>Company</th>
            <th>Priority</th>
            <th>Status</th>
            <th style="width:120px;">Open</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="6" class="muted">Loading…</td>
          </tr>
          <tr v-else-if="rows.length===0">
            <td colspan="6" class="muted">No inbox items.</td>
          </tr>
          <tr v-else v-for="d in rows" :key="d.id">
            <td><b>{{ d.refNo }}</b></td>
            <td>{{ d.title }}</td>
            <td>{{ d.companyName }}</td>
            <td><span class="pill" :class="'pill-'+d.priority">{{ d.priority }}</span></td>
            <td><span class="pill" :class="'pill-'+d.status">{{ d.status }}</span></td>
            <td><button class="btn btn-sm" @click="open(d.id)">Open</button></td>
          </tr>
        </tbody>
      </table>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import AppLayout from "../layouts/AppLayout.vue";
import { listDocuments } from "../api/documents.api";
import { getCurrentUser } from "../auth/currentUser";

const router = useRouter();

const loading = ref(false);
const error = ref("");
const rows = ref([]);

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const user = getCurrentUser();
    const data = await listDocuments();
    const list = Array.isArray(data) ? data : (data?.content ?? data?.items ?? []);
    rows.value = list.filter((d) => d.currentOwnerUserId === user.id && d.status !== "ISSUED");
  } catch (e) {
    error.value = e?.message ?? "Failed to load inbox";
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function open(id) {
  router.push(`/documents/${id}`);
}

onMounted(() => {
  window.addEventListener("rms_user_changed", load);
  load();
});
</script>

<style scoped>
.pageHead { display:flex; align-items:center; justify-content:space-between; margin-bottom:14px; }
h2 { margin:0; }
.card { background:#fff; padding:16px; border-radius:10px; }
.table { width:100%; border-collapse:collapse; }
.table th, .table td { padding:12px; border-bottom:1px solid #eee; text-align:left; }
.muted { color:#6b7280; padding:20px; }

.btn { padding:10px 12px; border-radius:8px; border:1px solid #e5e7eb; background:#fff; cursor:pointer; }
.btn:hover { background:#f9fafb; }
.btn-sm { padding:7px 10px; font-size:12px; }

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
  background:#fef2f2; border:1px solid #fecaca; color:#991b1b;
  padding:10px 12px; border-radius:8px;
}
</style>