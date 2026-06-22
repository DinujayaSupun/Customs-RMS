<template>
  <div class="login-wrap">
    <div class="glow glow-a"></div>
    <div class="glow glow-b"></div>

    <div class="login-shell">
      <section class="brand-panel">
        <div class="brand-mark">
          <span class="logo-frame">
            <img src="/sri-lanka-customs-logo.svg" alt="Sri Lanka Customs" />
          </span>
          <span>
            <span class="brand-kicker">Sri Lanka Customs</span>
            <span class="brand-name">Report Management System</span>
          </span>
        </div>

        <div class="brand-content">
          <div class="brand-badge">Official Clearance Workflow</div>
          <h1>Secure Document Routing for Modern Clearance Teams</h1>
          <p class="brand-copy">
            Coordinate approvals, forwarding, and escalations through one trusted workflow.
          </p>
        </div>

        <div class="brand-meta">
          <div class="meta-item">
            <span class="meta-value">Secure</span>
            <span class="meta-label">Authorized access only</span>
          </div>
          <div class="meta-item">
            <span class="meta-value">Compliant</span>
            <span class="meta-label">Customs regulations aligned</span>
          </div>
          <div class="meta-item">
            <span class="meta-value">Tracked</span>
            <span class="meta-label">Full document audit trail</span>
          </div>
        </div>
      </section>

      <section class="form-panel">
        <div class="card">
          <div class="card-logo">
            <img src="/sri-lanka-customs-logo.svg" alt="Sri Lanka Customs" />
          </div>
          <div class="card-head">
            <h2>Sign in</h2>
            <p class="sub">Use your RMS account credentials to continue.</p>
          </div>

          <form class="form" @submit.prevent="submit">
            <div class="row">
              <label>Username</label>
              <input id="username" v-model="username" class="input" placeholder="Enter username" autocomplete="username" />
            </div>

            <div class="row">
              <label>Password</label>
              <div class="passwordField">
                <input
                  id="password"
                  v-model="password"
                  class="input passwordInput"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="Enter password"
                  autocomplete="current-password"
                />
                <button
                  type="button"
                  class="passwordToggle"
                  :aria-label="showPassword ? 'Hide password' : 'Show password'"
                  :aria-pressed="showPassword"
                  @click="showPassword = !showPassword"
                >
                  <EyeOff v-if="showPassword" class="passwordIcon" aria-hidden="true" />
                  <Eye v-else class="passwordIcon" aria-hidden="true" />
                </button>
              </div>
            </div>

            <button class="btn btn-primary" type="submit" :disabled="busy">
              {{ busy ? "Signing in..." : "Sign In" }}
            </button>
          </form>

          <div v-if="error" class="err">{{ error }}</div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { Eye, EyeOff } from "lucide-vue-next";
import { login } from "../api/auth.api";
import { setSession } from "../auth/currentUser";
import { safeLoginRedirect } from "../router/authGuardLogic";

const router = useRouter();
const route = useRoute();

const username = ref("");
const password = ref("");
const showPassword = ref(false);
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

    const redirect = safeLoginRedirect(route.query.redirect);
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
    linear-gradient(135deg, rgba(15, 45, 86, 0.08), rgba(15, 45, 86, 0) 34%),
    linear-gradient(180deg, #f4f7fb 0%, #eaf1f7 100%);
  padding: 24px;
  font-family: "Manrope", "Segoe UI", "Helvetica Neue", sans-serif;
}

.glow {
  position: absolute;
  border-radius: 999px;
  filter: blur(58px);
  pointer-events: none;
}

.glow-a {
  width: 260px;
  height: 260px;
  top: -70px;
  right: 9%;
  background: rgba(34, 94, 168, 0.18);
}

.glow-b {
  width: 200px;
  height: 200px;
  bottom: -48px;
  left: 7%;
  background: rgba(14, 116, 144, 0.14);
}

.login-shell {
  position: relative;
  z-index: 1;
  width: min(1060px, 100%);
  display: grid;
  grid-template-columns: 1.15fr 0.95fr;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid rgba(124, 143, 164, 0.35);
  box-shadow: 0 26px 64px rgba(15, 32, 54, 0.18);
  background: #ffffff;
}

.card {
  width: 100%;
  max-width: 460px;
  background: #ffffff;
  border: 1px solid #d5e0ec;
  border-radius: 14px;
  padding: 28px;
  box-shadow: 0 18px 42px rgba(15, 32, 54, 0.08);
}

.brand-panel {
  padding: clamp(26px, 4vw, 42px);
  background:
    linear-gradient(160deg, rgba(9, 31, 66, 0.98) 0%, rgba(16, 59, 101, 0.97) 62%, rgba(18, 82, 112, 0.96) 100%),
    linear-gradient(120deg, rgba(245, 158, 11, 0.08), rgba(20, 184, 166, 0.08));
  color: #e6f2ff;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 28px;
}

.brand-mark {
  display: flex;
  align-items: center;
  gap: 14px;
}

.logo-frame {
  width: 66px;
  height: 66px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border-radius: 50%;
  background: #ffffff;
  border: 1px solid rgba(191, 219, 254, 0.5);
  box-shadow: 0 14px 34px rgba(8, 17, 33, 0.24);
}

.logo-frame img,
.card-logo img {
  width: 72%;
  height: 72%;
  object-fit: contain;
}

.brand-kicker,
.brand-name {
  display: block;
}

.brand-kicker {
  color: rgba(219, 234, 254, 0.78);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.brand-name {
  margin-top: 2px;
  color: #ffffff;
  font-size: 20px;
  font-weight: 800;
}

.brand-content {
  display: grid;
  gap: 14px;
}

.brand-badge {
  width: fit-content;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: #f7d48b;
  border: 1px solid rgba(247, 212, 139, 0.38);
  background: rgba(255, 255, 255, 0.07);
  border-radius: 999px;
  padding: 6px 12px;
}

.brand-panel h1 {
  margin: 0;
  max-width: 520px;
  font-size: clamp(1.58rem, 2.25vw, 2.2rem);
  line-height: 1.2;
  color: #f8fcff;
}

.brand-copy {
  margin: 0;
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
  border-radius: 10px;
  padding: 12px;
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
  background: linear-gradient(180deg, #ffffff, #f8fbff);
}

.card-logo {
  width: 58px;
  height: 58px;
  display: none;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  border-radius: 50%;
  background: #ffffff;
  border: 1px solid #d5e0ec;
  box-shadow: 0 12px 26px rgba(15, 32, 54, 0.1);
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
  height: 44px;
  border-radius: 8px;
  border: 1px solid #d1dbe8;
  background: #f8fafc;
  padding: 0 12px;
  color: #0f172a;
  font-size: 14px;
  outline: none;
  transition: border-color .2s ease, box-shadow .2s ease, background-color .2s ease;
}

.input:focus {
  border-color: #2563eb;
  background: #ffffff;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.14);
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
  right: 8px;
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transform: translateY(-50%);
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
}

.passwordToggle:hover {
  background: #eaf1fb;
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

.btn {
  width: 100%;
  margin-top: 4px;
  padding: 12px 14px;
  border-radius: 8px;
  border: 1px solid #d6deea;
  background: #fff;
  cursor: pointer;
  font-weight: 700;
  letter-spacing: 0.01em;
  transition: transform .18s ease, box-shadow .18s ease, background-color .18s ease;
}

.btn-primary {
  background: linear-gradient(140deg, #1d4ed8, #173b8f);
  border-color: #173b8f;
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

@media (max-width: 900px) {
  .login-shell {
    grid-template-columns: 1fr;
    max-width: 520px;
  }

  .brand-panel {
    gap: 14px;
  }

  .brand-mark {
    align-items: flex-start;
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

  .brand-mark {
    display: none;
  }

  .card-logo {
    display: inline-flex;
  }

  .card,
  .brand-panel,
  .form-panel {
    border-radius: 14px;
  }
}
</style>
