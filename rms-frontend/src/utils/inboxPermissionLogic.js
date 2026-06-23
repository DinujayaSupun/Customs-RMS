function normalizePermissions(permissions) {
  if (!Array.isArray(permissions)) return [];
  return [...new Set(permissions.map((value) => String(value || "").trim().toUpperCase()).filter(Boolean))];
}

function hasPermission(user, permission) {
  if (!user || !permission) return false;
  return normalizePermissions(user.permissions).includes(String(permission).trim().toUpperCase());
}

export function canForwardInboxDocument({
  doc,
  user,
  inboxMode,
  forwardReturnAllowedStatuses = [],
  availableForwardVisibilities = [],
}) {
  if (!doc || inboxMode !== "received") return false;
  if (doc.canWorkflow === false) return false;

  const ownerId = Number(doc.currentOwnerUserId ?? doc.currentOwnerId ?? doc.ownerUserId);
  const userId = Number(user?.id);
  const docStatus = String(doc.status || "").toUpperCase();

  return Number.isFinite(userId)
    && ownerId === userId
    && forwardReturnAllowedStatuses.includes(docStatus)
    && hasPermission(user, "FORWARD_DOCUMENT")
    && availableForwardVisibilities.length > 0;
}

export function canReturnInboxDocument({
  doc,
  user,
  forwardReturnAllowedStatuses = [],
}) {
  if (!doc) return false;
  if (doc.canWorkflow === false) return false;

  const ownerId = Number(doc.currentOwnerUserId ?? doc.currentOwnerId ?? doc.ownerUserId);
  const userId = Number(user?.id);
  const docStatus = String(doc.status || "").toUpperCase();

  return Number.isFinite(userId)
    && ownerId === userId
    && forwardReturnAllowedStatuses.includes(docStatus)
    && hasPermission(user, "RETURN_DOCUMENT");
}
