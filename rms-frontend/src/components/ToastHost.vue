<template>
  <div class="toast-host" :style="{ top: `${topOffset}px` }" aria-live="polite" aria-atomic="true">
    <TransitionGroup name="toast" tag="div" class="toast-list">
      <article
        v-for="toast in toasts"
        :key="toast.id"
        class="toast-item"
        :class="`toast-${toast.type}`"
        role="status"
      >
        <div class="toast-content">
          <span class="toast-message">{{ toast.message }}</span>
          <button
            v-if="toast.route"
            class="toast-action"
            @click="openToastRoute(toast)"
            :aria-label="toast.actionLabel || 'Open item'"
          >
            {{ toast.actionLabel || "Open" }}
          </button>
        </div>
        <button class="toast-close" @click="remove(toast.id)" aria-label="Dismiss notification">×</button>
      </article>
    </TransitionGroup>
  </div>
</template>

<script setup>
import { useRouter } from "vue-router";
import { useToast } from "../composables/useToast";

defineProps({
  topOffset: {
    type: Number,
    default: 72,
  },
});

const { toasts, remove } = useToast();
const router = useRouter();

function openToastRoute(toast) {
  if (!toast?.route) return;
  router.push(toast.route);
  remove(toast.id);
}
</script>

<style scoped>
.toast-host {
  position: fixed;
  right: 1rem;
  z-index: 2200;
  pointer-events: none;
}

.toast-list {
  display: grid;
  gap: 0.625rem;
}

.toast-item {
  min-width: 250px;
  max-width: 360px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.75rem 0.875rem;
  border-radius: 0.75rem;
  border: 1px solid transparent;
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.18);
  backdrop-filter: blur(4px);
  pointer-events: auto;
}

.toast-content {
  display: grid;
  gap: 0.35rem;
}

.toast-message {
  color: #0f172a;
  font-size: 0.9rem;
  line-height: 1.3;
}

.toast-action {
  border: none;
  background: transparent;
  color: #1d4ed8;
  padding: 0;
  font-size: 0.82rem;
  font-weight: 700;
  text-align: left;
  cursor: pointer;
}

.toast-action:hover {
  text-decoration: underline;
}

.toast-close {
  border: none;
  background: transparent;
  color: #334155;
  font-size: 1.1rem;
  line-height: 1;
  cursor: pointer;
  padding: 0;
}

.toast-success {
  background: #ecfdf5;
  border-color: #86efac;
}

.toast-error {
  background: #fef2f2;
  border-color: #fca5a5;
}

.toast-info {
  background: #eff6ff;
  border-color: #93c5fd;
}

.toast-warning {
  background: #fffbeb;
  border-color: #fcd34d;
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.22s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px) translateX(6px);
}

@media (max-width: 640px) {
  .toast-host {
    right: 0.75rem;
    left: 0.75rem;
  }

  .toast-item {
    min-width: 0;
    max-width: none;
  }
}
</style>
