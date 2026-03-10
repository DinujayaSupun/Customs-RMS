<template>
  <div class="toast-host" aria-live="polite" aria-atomic="true">
    <TransitionGroup name="toast" tag="div" class="toast-list">
      <article
        v-for="toast in toasts"
        :key="toast.id"
        class="toast-item"
        :class="`toast-${toast.type}`"
        role="status"
      >
        <span class="toast-message">{{ toast.message }}</span>
        <button class="toast-close" @click="remove(toast.id)" aria-label="Dismiss notification">×</button>
      </article>
    </TransitionGroup>
  </div>
</template>

<script setup>
import { useToast } from "../composables/useToast";

const { toasts, remove } = useToast();
</script>

<style scoped>
.toast-host {
  position: fixed;
  top: 1rem;
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

.toast-message {
  color: #0f172a;
  font-size: 0.9rem;
  line-height: 1.3;
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
    top: 0.75rem;
    right: 0.75rem;
    left: 0.75rem;
  }

  .toast-item {
    min-width: 0;
    max-width: none;
  }
}
</style>
