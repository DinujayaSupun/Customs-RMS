export const INBOX_PRIORITY_ORDER = { LOW: 1, MEDIUM: 2, HIGH: 3, URGENT: 4 };
export const INBOX_STATUS_ORDER = { PENDING: 1, IN_PROGRESS: 2, APPROVED: 3, ISSUED: 4, REJECTED: 5 };

function toText(value) {
  return String(value ?? "").trim().toLowerCase();
}

export function toInboxRecentScore(doc) {
  const source = doc?.inboxReceivedAt ?? doc?.sentAt ?? doc?.updatedAt ?? doc?.receivedDate ?? doc?.createdAt;
  const parsed = Date.parse(source);
  if (!Number.isNaN(parsed)) return parsed;
  const idNumber = Number(doc?.id);
  return Number.isFinite(idNumber) ? idNumber : 0;
}

export function toInboxPriorityScore(doc) {
  return INBOX_PRIORITY_ORDER[String(doc?.priority || "").toUpperCase()] ?? 0;
}

export function sortInboxDefaultDisplay(list) {
  return [...list].sort((a, b) => {
    const priorityDiff = toInboxPriorityScore(b) - toInboxPriorityScore(a);
    if (priorityDiff !== 0) return priorityDiff;
    return toInboxRecentScore(b) - toInboxRecentScore(a);
  });
}

export function sortInboxDocumentsBy(list, sortBy) {
  const arr = [...list];
  switch (sortBy) {
    case "ref_asc":
      return arr.sort((a, b) => toText(a.refNo).localeCompare(toText(b.refNo)));
    case "ref_desc":
      return arr.sort((a, b) => toText(b.refNo).localeCompare(toText(a.refNo)));
    case "title_asc":
      return arr.sort((a, b) => toText(a.title).localeCompare(toText(b.title)));
    case "priority_desc":
      return arr.sort((a, b) => (INBOX_PRIORITY_ORDER[b.priority] ?? 0) - (INBOX_PRIORITY_ORDER[a.priority] ?? 0));
    case "status_asc":
      return arr.sort((a, b) => (INBOX_STATUS_ORDER[a.status] ?? 999) - (INBOX_STATUS_ORDER[b.status] ?? 999));
    case "recent":
    default:
      return arr.sort((a, b) => toInboxRecentScore(b) - toInboxRecentScore(a));
  }
}

export function markInboxDocumentViewed(list, documentId) {
  const targetId = Number(documentId);
  if (!Number.isFinite(targetId)) return Array.isArray(list) ? [...list] : [];

  return (Array.isArray(list) ? list : []).map((doc) => {
    const rowId = Number(doc?.documentId ?? doc?.id);
    return rowId === targetId ? { ...doc, viewedByMe: true } : doc;
  });
}

function userLabel({ id, name, role }) {
  const cleanName = String(name || "").trim();
  const cleanRole = String(role || "").trim();
  if (cleanName && cleanRole) return `${cleanName} (${cleanRole})`;
  if (cleanName) return cleanName;
  if (cleanRole) return `Unknown user (${cleanRole})`;
  return "Unknown user";
}

export function buildInboxReceivedPreview(doc, formatTime = (value) => value || "time unknown", currentUserId = null) {
  const senderId = doc?.inboxSenderUserId ?? null;
  const minuteAuthorId = doc?.latestRemarkByUserId ?? null;
  const sender = userLabel({
    id: senderId,
    name: doc?.inboxSenderName,
    role: doc?.inboxSenderRole,
  });
  const minuteText = String(doc?.latestRemarkTextPreview || "").trim();
  const minuteTooltip = String(doc?.latestRemarkText || doc?.latestRemarkTextPreview || "").trim() || null;
  const undoAction = String(doc?.undoSendActionType || "").toUpperCase() === "UNDO_SEND";

  if (undoAction) {
    const undoActorId = doc?.undoSendByUserId ?? null;
    const undoActor = undoActorId != null && currentUserId != null && Number(undoActorId) === Number(currentUserId)
      ? "you"
      : userLabel({
        id: undoActorId,
        name: doc?.undoSendByName,
        role: doc?.undoSendByRole,
      });
    const undoFrom = userLabel({
      id: doc?.undoSendFromUserId,
      name: doc?.undoSendFromName,
      role: doc?.undoSendFromRole,
    });
    const senderLine = `Send undone by ${undoActor} from ${undoFrom}`;

    if (!minuteText) {
      return {
        senderLine,
        minuteLine: null,
        minuteTooltip: null,
        fallbackLine: "No minute added",
      };
    }

    const minuteAuthor = userLabel({
      id: minuteAuthorId,
      name: doc?.latestRemarkByName,
      role: doc?.latestRemarkByRole,
    });

    return {
      senderLine,
      minuteLine: `Last minute by ${minuteAuthor} • ${formatTime(doc?.latestRemarkAt)} – ${minuteText}`,
      minuteTooltip,
      fallbackLine: null,
    };
  }

  if (!minuteText) {
    return {
      senderLine: `Sent by ${sender}`,
      minuteLine: null,
      minuteTooltip: null,
      fallbackLine: "No minute added",
    };
  }

  const minuteTime = formatTime(doc?.latestRemarkAt);
  const minuteSuffix = `${minuteTime} – ${minuteText}`;
  const sameUser = senderId != null
    && minuteAuthorId != null
    && Number(senderId) === Number(minuteAuthorId);

  if (sameUser) {
    return {
      senderLine: `Sent by ${sender} • ${minuteSuffix}`,
      minuteLine: null,
      minuteTooltip,
      fallbackLine: null,
    };
  }

  const minuteAuthor = userLabel({
    id: minuteAuthorId,
    name: doc?.latestRemarkByName,
    role: doc?.latestRemarkByRole,
  });

  return {
    senderLine: `Sent by ${sender}`,
    minuteLine: `Last minute by ${minuteAuthor} • ${minuteSuffix}`,
    minuteTooltip,
    fallbackLine: null,
  };
}

export function findPreferredReturnTargetId({ canReturn, currentUserId, forwardTargets, forwardMovements }) {
  if (!canReturn || !currentUserId || !Array.isArray(forwardTargets) || forwardTargets.length === 0) return null;

  const validIds = new Set(forwardTargets.map((u) => Number(u.id)));
  for (let index = forwardMovements.length - 1; index >= 0; index -= 1) {
    const movement = forwardMovements[index];
    const actionType = String(movement?.actionType || "").toUpperCase();
    const toUserId = Number(movement?.toUserId);
    const fromUserId = Number(movement?.fromUserId);
    if (!["FORWARD", "RETURN"].includes(actionType)) continue;
    if (toUserId !== Number(currentUserId)) continue;
    if (!Number.isFinite(fromUserId) || !validIds.has(fromUserId)) continue;
    return fromUserId;
  }

  return null;
}

export function resolveWorkflowAutoTarget({ candidateTargets, preferredReturnTargetId, currentTargetId, autoSelectedTargetId }) {
  const valid = new Set((candidateTargets || []).map((x) => Number(x.id)));
  const desiredTargetId = preferredReturnTargetId != null
    ? Number(preferredReturnTargetId)
    : (candidateTargets?.[0] ? Number(candidateTargets[0].id) : null);
  const normalizedCurrentTargetId = currentTargetId == null ? null : Number(currentTargetId);
  const currentIsValid = normalizedCurrentTargetId != null && valid.has(normalizedCurrentTargetId);
  const shouldAutoSelect = !currentIsValid || (
    autoSelectedTargetId != null &&
    Number(autoSelectedTargetId) === normalizedCurrentTargetId
  );

  if (shouldAutoSelect) {
    return {
      targetId: desiredTargetId,
      autoSelectedTargetId: desiredTargetId,
    };
  }

  return {
    targetId: normalizedCurrentTargetId,
    autoSelectedTargetId,
  };
}
