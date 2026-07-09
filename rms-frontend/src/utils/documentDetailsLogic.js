function normalizePermissions(permissions) {
  if (!Array.isArray(permissions)) return [];
  return [...new Set(permissions.map((value) => String(value || "").trim().toUpperCase()).filter(Boolean))];
}

function hasPermission(user, permission) {
  if (!user || !permission) return false;
  return normalizePermissions(user.permissions).includes(String(permission).trim().toUpperCase());
}

function isOwner(doc, user) {
  if (!doc) return false;
  // The literal current_owner_user_id check covers person-held documents. A group-held document's
  // anchor is just one admin, but any admin of that group can act - the backend already resolves
  // that (canActOnDocument) into doc.canWorkflow, so trust it as a second, additive path here
  // rather than re-deriving group membership on the client.
  return Number(doc.currentOwnerUserId) === Number(user?.id) || Boolean(doc.canWorkflow);
}

function isIssued(doc) {
  return String(doc?.status || "").toUpperCase() === "ISSUED";
}

export function getAvailableForwardVisibilities(user) {
  const options = [];
  if (hasPermission(user, "FORWARD_PUBLIC")) options.push("PUBLIC");
  if (hasPermission(user, "FORWARD_PRIVATE")) options.push("PRIVATE");
  return options;
}

export function getDaysOpenDisplay(doc, nowMs = Date.now()) {
  if (isIssued(doc)) return "Closed";

  const received = doc?.receivedDate;
  if (!received) return "-";

  const start = new Date(received);
  if (Number.isNaN(start.getTime())) return "-";

  const dayMs = 24 * 60 * 60 * 1000;
  const diff = Math.floor((nowMs - start.getTime()) / dayMs);
  return String(Math.max(0, diff));
}

export function getDocumentDetailsCapabilities({
  doc,
  user,
  approveRejectButtonsEnabled,
  forwardReturnAllowedStatuses = [],
}) {
  const owner = isOwner(doc, user);
  const issued = isIssued(doc);
  // Once a decision is made (approved or rejected) the backend blocks BOTH approve and reject;
  // the only way forward is reopen. Mirror that so neither button leaks through on a decided document.
  const decided = ["APPROVED", "REJECTED"].includes(String(doc?.status || "").toUpperCase());
  const canViewAllHistory = hasPermission(user, "VIEW_ALL_HISTORY");
  const canViewRemarks = !!doc && (owner || hasPermission(user, "VIEW_REMARKS_WHEN_NOT_REPORT_AT"));
  const isEditLocked = !!doc && (!!doc.completedAt || issued);
  const availableForwardVisibilities = getAvailableForwardVisibilities(user);
  const canForwardReturnByStatus = !!doc
    && forwardReturnAllowedStatuses.includes(String(doc.status || "").toUpperCase());

  // Action buttons mirror backend workflow rules so users do not see controls they cannot use.
  const canForward = !!doc
    && canForwardReturnByStatus
    && owner
    && hasPermission(user, "FORWARD_DOCUMENT")
    && availableForwardVisibilities.length > 0;

  const canReturn = !!doc
    && canForwardReturnByStatus
    && owner
    && hasPermission(user, "RETURN_DOCUMENT");

  const canApprove = !!doc
    && approveRejectButtonsEnabled
    && !issued
    && !decided
    && owner
    && hasPermission(user, "APPROVE_DOCUMENT");

  const canReject = !!doc
    && approveRejectButtonsEnabled
    && !issued
    && !decided
    && owner
    && hasPermission(user, "REJECT_DOCUMENT");

  const canIssue = !!doc
    && owner
    && hasPermission(user, "ISSUE_DOCUMENT")
    && !doc.issuedAt
    && (
      approveRejectButtonsEnabled
        ? doc.status === "APPROVED"
        : doc.status !== "ISSUED"
    );

  const canReopen = !!doc
    && owner
    && hasPermission(user, "REOPEN_DOCUMENT")
    && (
      approveRejectButtonsEnabled
        ? !issued && ["APPROVED", "REJECTED"].includes(String(doc.status || "").toUpperCase())
        : ["ISSUED", "APPROVED", "REJECTED"].includes(String(doc.status || "").toUpperCase())
    );

  const canAddRemark = !!doc && owner && !issued && hasPermission(user, "ADD_REMARK");

  const canWorkflow = canForward || canReturn || canApprove || canReject || canIssue || canReopen;

  return {
    isOwner: owner,
    canViewAllHistory,
    canViewRemarks,
    isIssued: issued,
    isEditLocked,
    canEditDetails: !!doc && owner && !isEditLocked && hasPermission(user, "EDIT_DOCUMENT_DETAILS"),
    canViewHistory: !!doc && (owner || canViewAllHistory),
    canUploadAttachments: !!doc && owner && !issued && hasPermission(user, "UPLOAD_ATTACHMENT"),
    availableForwardVisibilities,
    canForwardReturnByStatus,
    canForward,
    canReturn,
    canChooseWorkflowTarget: canForward || canReturn,
    canApprove,
    canReject,
    canIssue,
    canReopen,
    canAddRemark,
    canTypeRemark: canAddRemark || canWorkflow,
    canWorkflow,
    daysOpenDisplay: getDaysOpenDisplay(doc),
  };
}

function parseDateMs(value) {
  if (!value) return null;
  const parsed = Date.parse(value);
  return Number.isNaN(parsed) ? null : parsed;
}

export function buildMovementRemarksMap(movements, remarks, maxDeltaMs = 10 * 60 * 1000) {
  const result = new Map();
  const movementsByUserId = new Map();

  for (const movement of movements || []) {
    result.set(movement.id, []);

    const actionTime = parseDateMs(movement?.actionAt);
    const actionByUserId = Number(movement?.actionByUserId);
    if (actionTime == null || !Number.isFinite(actionByUserId)) continue;

    const userMovements = movementsByUserId.get(actionByUserId) || [];
    userMovements.push({ movement, actionTime });
    movementsByUserId.set(actionByUserId, userMovements);
  }

  for (const userMovements of movementsByUserId.values()) {
    userMovements.sort((a, b) => a.actionTime - b.actionTime);
  }

  for (const remark of remarks || []) {
    const remarkTime = parseDateMs(remark?.remarkedAt);
    const remarkUserId = Number(remark?.remarkedByUserId);
    if (remarkTime == null || !Number.isFinite(remarkUserId)) continue;

    const userMovements = movementsByUserId.get(remarkUserId) || [];
    let bestMovement = null;
    let bestDelta = Number.POSITIVE_INFINITY;

    for (const { movement, actionTime } of userMovements) {
      const delta = actionTime - remarkTime;
      if (delta < 0) continue;
      if (delta > maxDeltaMs) break;
      if (delta < bestDelta) {
        bestDelta = delta;
        bestMovement = movement;
      }
    }

    if (bestMovement && result.has(bestMovement.id)) {
      result.get(bestMovement.id).push(remark);
    }
  }

  return result;
}
