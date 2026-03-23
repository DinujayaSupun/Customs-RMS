<template>
  <div class="login-wrap">
    <div class="glow glow-a"></div>
    <div class="glow glow-b"></div>

    <div class="login-shell">
      <section class="brand-panel">
        <div class="brand-badge">Customs RMS</div>
        <h1>Secure Document Routing for Modern Clearance Teams</h1>
        <p class="brand-copy">
          Coordinate approvals, forwarding, and escalations through one trusted workflow.
        </p>

        <div class="brand-meta">
          <div class="meta-item">
            <span class="meta-value">7+</span>
            <span class="meta-label">Role lanes</span>
          </div>
          <div class="meta-item">
            <span class="meta-value">Live</span>
            <span class="meta-label">Visibility control</span>
          </div>
          <div class="meta-item">
            <span class="meta-value">Auto</span>
            <span class="meta-label">DC escalation</span>
          </div>
        </div>
      </section>

      <section class="form-panel">
        <div class="card">
          <div class="card-head">
            <h2>Sign in</h2>
            <p class="sub">Use your RMS account credentials to continue.</p>
          </div>

          <form class="form" @submit.prevent="submit">
            <div class="row">
              <label>Username</label>
              <input v-model="username" class="input" placeholder="dc" autocomplete="username" />
            </div>

            <div class="row">
              <label>Password</label>
              <input v-model="password" class="input" type="password" placeholder="Pass@123" autocomplete="current-password" />
            </div>

            <button class="btn btn-primary" type="submit" :disabled="busy">
              {{ busy ? "Signing in..." : "Sign In" }}
            </button>
          </form>

          <div v-if="error" class="err">{{ error }}</div>

          <div class="hintRow">
            <span class="hintLabel">Need default credentials?</span>
            <HoverHint text="Default users: dc, ddc, sc, asc, pma. Default password: Pass@123." />
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import HoverHint from "../components/HoverHint.vue";
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
      hasProfilePicture: !!res.hasProfilePicture,
      profilePictureUpdatedAt: res.profilePictureUpdatedAt || null,
      permissions: res.permissions || [],
      name: res.fullName,
    });

    // Defer a personalized welcome toast to the first authenticated screen.
    const welcomePayload = {
      fullName: res.fullName || res.username || "User",
      role: res.role || "USER",
      at: Date.now(),
    };
    window.sessionStorage.setItem("rms_pending_welcome", JSON.stringify(welcomePayload));

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
@import url("https://fonts.googleapis.com/css2?family=Manrope:wght@500;600;700;800&display=swap");

.login-wrap {
  position: relative;
  overflow: hidden;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at 14% 14%, rgba(9, 64, 103, 0.16), rgba(9, 64, 103, 0) 42%),
    radial-gradient(circle at 88% 84%, rgba(16, 185, 129, 0.13), rgba(16, 185, 129, 0) 38%),
    linear-gradient(155deg, #eef5fb 0%, #f8fbfd 54%, #edf6ff 100%);
  padding: 24px;
  font-family: "Manrope", "Segoe UI", "Helvetica Neue", sans-serif;
}

.glow {
  position: absolute;
  border-radius: 999px;
  filter: blur(42px);
  pointer-events: none;
}

.glow-a {
  width: 260px;
  height: 260px;
  top: -70px;
  right: 9%;
  background: rgba(37, 99, 235, 0.3);
}

.glow-b {
  width: 200px;
  height: 200px;
  bottom: -48px;
  left: 7%;
  background: rgba(13, 148, 136, 0.28);
}

.login-shell {
  position: relative;
  z-index: 1;
  width: min(1040px, 100%);
  display: grid;
  grid-template-columns: 1.15fr 0.95fr;
  border-radius: 22px;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.35);
  box-shadow: 0 30px 70px rgba(15, 23, 42, 0.16);
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(4px);
}

.card {
  width: 100%;
  max-width: 460px;
  background: #ffffff;
  border: 1px solid #dbe8f5;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.08);
}

.brand-panel {
  padding: clamp(26px, 4vw, 42px);
  background:
    linear-gradient(160deg, rgba(14, 35, 73, 0.96) 0%, rgba(15, 54, 95, 0.95) 58%, rgba(18, 72, 99, 0.93) 100%),
    linear-gradient(120deg, rgba(56, 189, 248, 0.08), rgba(20, 184, 166, 0.07));
  color: #e6f2ff;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 20px;
}

.brand-badge {
  width: fit-content;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: #d2ebff;
  border: 1px solid rgba(191, 219, 254, 0.36);
  background: rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  padding: 6px 12px;
}

.brand-panel h1 {
  margin: 0;
  max-width: 520px;
  font-size: clamp(1.5rem, 2.2vw, 2.15rem);
  line-height: 1.2;
  letter-spacing: -0.02em;
  color: #f8fcff;
}

.brand-copy {
  margin: 8px 0 0;
  color: rgba(226, 240, 255, 0.84);
  font-size: 14px;
  line-height: 1.65;
  max-width: 520px;
}

.brand-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  border-radius: 12px;
  padding: 10px;
  background: rgba(148, 192, 255, 0.09);
  border: 1px solid rgba(191, 219, 254, 0.22);
}

.meta-value {
  color: #f3fbff;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.meta-label {
  color: rgba(216, 234, 255, 0.88);
  font-size: 11px;
}

.form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: clamp(20px, 3.1vw, 34px);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(248, 251, 255, 0.86));
}

.card-head h2 {
  margin: 0;
  font-size: 1.45rem;
  line-height: 1.2;
  color: #0f172a;
  letter-spacing: -0.02em;
}

.sub {
  margin: 6px 0 16px;
  color: #64748b;
  font-size: 13px;
}

.row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
}

label {
  font-size: 12px;
  font-weight: 700;
  color: #334155;
  letter-spacing: 0.02em;
}

.input {
  height: 42px;
  border-radius: 10px;
  border: 1px solid #d1dbe8;
  background: #ffffff;
  padding: 0 12px;
  color: #0f172a;
  font-size: 14px;
  outline: none;
  transition: border-color .2s ease, box-shadow .2s ease, background-color .2s ease;
}

.input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.14);
}

.btn {
  width: 100%;
  margin-top: 2px;
  padding: 11px 14px;
  border-radius: 10px;
  border: 1px solid #d6deea;
  background: #fff;
  cursor: pointer;
  font-weight: 700;
  letter-spacing: 0.01em;
  transition: transform .18s ease, box-shadow .18s ease, background-color .18s ease;
}

.btn-primary {
  background: linear-gradient(140deg, #2563eb, #1e40af);
  border-color: #1e40af;
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.3);
}

.btn:disabled {
  opacity: 0.75;
  cursor: not-allowed;
}

.err {
  margin-top: 14px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #991b1b;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
}

.hintRow {
  margin-top: 14px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.hintLabel {
  font-size: 12px;
  color: #64748b;
}

@media (max-width: 900px) {
  .login-shell {
    grid-template-columns: 1fr;
    max-width: 520px;
  }

  .brand-panel {
    gap: 14px;
  }

  .brand-meta {
    grid-template-columns: 1fr 1fr 1fr;
  }
}

@media (max-width: 600px) {
  .login-wrap {
    padding: 14px;
  }

  .brand-meta {
    grid-template-columns: 1fr;
  }

  .card,
  .brand-panel,
  .form-panel {
    border-radius: 14px;
  }
}
</style>
