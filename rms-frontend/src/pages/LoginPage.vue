<template>
  <div class="login-wrap">
    <div class="card">
      <h1>RMS Login</h1>
      <p class="sub">Use seeded account credentials to continue.</p>

      <div class="row">
        <label>Username</label>
        <input v-model="username" class="input" placeholder="dc" />
      </div>

      <div class="row">
        <label>Password</label>
        <input v-model="password" class="input" type="password" placeholder="Pass@123" />
      </div>

      <button class="btn btn-primary" :disabled="busy" @click="submit">
        {{ busy ? "Signing in..." : "Sign In" }}
      </button>

      <div v-if="error" class="err">{{ error }}</div>

      <div class="hint">
        Default users: <b>dc</b>, <b>ddc</b>, <b>sc</b>, <b>asc</b>, <b>pma</b><br />
        Password: <b>Pass@123</b>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { login } from "../api/auth.api";
import { setSession } from "../auth/currentUser";

const router = useRouter();
const route = useRoute();

const username = ref("dc");
const password = ref("Pass@123");
const busy = ref(false);
const error = ref("");

async function submit() {
  error.value = "";
  busy.value = true;
  try {
    const res = await login(username.value.trim(), password.value);

    setSession(res.accessToken, {
      id: res.userId,
      username: res.username,
      fullName: res.fullName,
      role: res.role,
      permissions: res.permissions || [],
      name: res.fullName,
    });

    const redirect = route.query.redirect ? String(route.query.redirect) : "/documents";
    router.replace(redirect);
  } catch (e) {
    error.value = e?.message || "Login failed.";
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f3f4f6;
  padding: 20px;
}

.card {
  width: 100%;
  max-width: 430px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
}

h1 {
  margin: 0;
  font-size: 22px;
}

.sub {
  margin: 6px 0 16px;
  color: #6b7280;
  font-size: 13px;
}

.row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}

label {
  font-size: 12px;
  font-weight: 700;
  color: #374151;
}

.input {
  height: 38px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  padding: 0 10px;
  outline: none;
}

.btn {
  width: 100%;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #fff;
  cursor: pointer;
}

.btn-primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.err {
  margin-top: 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #991b1b;
  padding: 10px;
  border-radius: 8px;
  font-size: 13px;
}

.hint {
  margin-top: 12px;
  font-size: 12px;
  color: #6b7280;
}
</style>
