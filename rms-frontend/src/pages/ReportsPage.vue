<template>
  <AppLayout>
    <h2 style="margin-bottom:20px;">Reports</h2>

    <div class="card">
      <table>
        <thead>
          <tr>
            <th>Ref No</th>
            <th>Title</th>
            <th>Company</th>
            <th>Priority</th>
            <th>Status</th>
          </tr>
        </thead>

        <tbody>
          <tr v-for="r in reports" :key="r.id">
            <td>{{ r.refNo }}</td>
            <td>{{ r.title }}</td>
            <td>{{ r.companyName }}</td>
            <td>{{ r.priority }}</td>
            <td>{{ r.status }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from "vue";
import AppLayout from "../layouts/AppLayout.vue";

const reports = ref([]);

onMounted(async () => {
  const res = await fetch("/api/reports");
  reports.value = await res.json();
});
</script>

<style scoped>
.card {
  background: white;
  padding: 20px;
  border-radius: 10px;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th, td {
  padding: 12px;
  border-bottom: 1px solid #eee;
  text-align: left;
}
</style>
