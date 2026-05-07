function normalizePermissions(permissions) {
  if (!Array.isArray(permissions)) return [];
  return [...new Set(permissions.map((value) => String(value || "").trim().toUpperCase()).filter(Boolean))];
}

function hasPermission(user, permission) {
  if (!user || !permission) return false;
  return normalizePermissions(user.permissions).includes(String(permission).trim().toUpperCase());
}

function isOwner(doc, user) {
  return !!doc && Number(doc.currentOwnerUserId) === Number(user?.id);
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
  const canViewAllHistory = hasPermission(user, "VIEW_ALL_HISTORY");
  const canViewRemarks = !!doc && (owner || hasPermission(user, "VIEW_REMARKS_WHEN_NOT_REPORT_AT"));
  const isEditLocked = !!doc && (!!doc.completedAt || issued);
  const availableForwardVisibilities = getAvailableForwardVisibilities(user);
  const canForwardReturnByStatus = !!doc
    && forwardReturnAllowedStatuses.includes(String(doc.status || "").toUpperCase());

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
    && owner
    && hasPermission(user, "APPROVE_DOCUMENT")
    && doc.status !== "APPROVED";

  const canReject = !!doc
    && approveRejectButtonsEnabled
    && !issued
    && owner
    && hasPermission(user, "REJECT_DOCUMENT")
    && doc.status !== "REJECTED";

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
    canTypeRemark: canAddRemark || canForward || canReturn || canApprove || canReject || canIssue || canReopen,
    daysOpenDisplay: getDaysOpenDisplay(doc),
  };
}
