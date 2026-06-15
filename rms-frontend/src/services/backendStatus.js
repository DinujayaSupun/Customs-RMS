let backendStatus = "unknown";

export function getBackendStatus() {
  return backendStatus;
}

export function setBackendStatus(status) {
  if (backendStatus === status) return;
  backendStatus = status;
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent("rms_backend_status_changed", {
    detail: {
      status,
      at: Date.now(),
    },
  }));
}
