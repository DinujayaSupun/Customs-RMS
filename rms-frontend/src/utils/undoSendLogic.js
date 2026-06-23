export function getUndoSendInfo(row = {}) {
  const status = String(row.undoSendStatus || "").toUpperCase();
  const showExpiredInfo = row.undoSendShowExpiredInfo !== false;
  const undoActor = formatUndoActor(row);
  const isReceiverUndoNotice = String(row.undoSendActionType || "").toUpperCase() === "UNDO_SEND";

  // Receiver-side undo records are informational only; they should never show another Undo button.
  if (undoActor && isReceiverUndoNotice) {
    return {
      canUndo: false,
      isUndoNotice: true,
      isReceiverUndoNotice: true,
      label: "",
      helper: `Undone by ${undoActor}`,
    };
  }

  if (undoActor) {
    return { canUndo: false, isUndoNotice: false, isReceiverUndoNotice: false, label: "", helper: "" };
  }

  if (row.canUndoSend) {
    return {
      canUndo: true,
      isUndoNotice: false,
      isReceiverUndoNotice: false,
      label: "Undo Send",
      helper: row.undoSendExpiresAt ? `Undo Send available until ${formatUndoExpiry(row.undoSendExpiresAt)}` : "Undo Send available",
    };
  }

  if (!showExpiredInfo) {
    return { canUndo: false, isUndoNotice: false, isReceiverUndoNotice: false, label: "", helper: "" };
  }

  const helper = {
    EXPIRED: "Undo Send expired",
    OPENED: "Undo Send unavailable: receiver already opened it",
    ACTION_NOT_ALLOWED: "Undo Send is not allowed for this action",
    ALREADY_MOVED: "Undo Send unavailable: document already moved",
    FINALIZED: "Undo Send unavailable: document is finalized",
    DISABLED: "Undo Send is disabled by admin",
  }[status] || "";

  return { canUndo: false, isUndoNotice: false, isReceiverUndoNotice: false, label: "", helper };
}

export function needsUndoReason(row = {}) {
  return row.undoSendRequiresReason !== false;
}

export function formatUndoExpiry(value) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "";
  return parsed.toLocaleString([], {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatUndoActor(row = {}) {
  const name = String(row.undoSendByName || "").trim();
  const role = String(row.undoSendByRole || "").trim();
  const userId = row.undoSendByUserId;

  if (name && role) return `${name} (${role})`;
  if (name) return name;
  if (role) return `Unknown user (${role})`;
  return userId == null ? "" : "Unknown user";
}
