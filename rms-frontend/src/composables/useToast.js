import { reactive, readonly } from "vue";

const toasts = reactive([]);
let nextId = 1;

function push(message, type = "info", duration = 3500, options = null) {
  const id = nextId++;
  const toast = {
    id,
    message,
    type,
    route: options?.route || null,
    actionLabel: options?.actionLabel || null,
  };
  toasts.push(toast);

  if (duration > 0) {
    window.setTimeout(() => remove(id), duration);
  }

  return id;
}

function remove(id) {
  const idx = toasts.findIndex((t) => t.id === id);
  if (idx >= 0) {
    toasts.splice(idx, 1);
  }
}

export function useToast() {
  return {
    toasts: readonly(toasts),
    show: push,
    success: (message, duration) => push(message, "success", duration),
    error: (message, duration) => push(message, "error", duration),
    info: (message, duration) => push(message, "info", duration),
    infoWithAction: (message, route, actionLabel = "Open", duration = 4500) =>
      push(message, "info", duration, { route, actionLabel }),
    warning: (message, duration) => push(message, "warning", duration),
    remove,
  };
}
