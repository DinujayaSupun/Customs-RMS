function normalizePermissions(permissions) {
  if (!Array.isArray(permissions)) return [];
  return [...new Set(permissions.map((value) => String(value || "").trim().toUpperCase()).filter(Boolean))];
}

function hasPermission(user, permission) {
  if (!user || !permission) return false;
  return normalizePermissions(user.permissions).includes(String(permission).trim().toUpperCase());
}

export function getDocumentsPageDaysOpenDisplay(doc, nowMs = Date.now()) {
  if (String(doc?.status || "").toUpperCase() === "ISSUED") return "Closed";

  const raw = doc?.receivedDate;
  if (!raw) return "-";
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return "-";
  const dayMs = 24 * 60 * 60 * 1000;
  return Math.max(0, Math.floor((nowMs - date.getTime()) / dayMs));
}

export function canPreviewDocument(doc, user) {
  if (hasPermission(user, "VIEW_ALL_HISTORY")) return true;
  return Number(doc?.currentOwnerUserId) === Number(user?.id);
}

export function canSeePreviewOperational(doc, user) {
  if (doc?.canViewTimeline !== undefined && doc?.canViewTimeline !== null) {
    return !!doc.canViewTimeline;
  }
  return canPreviewDocument(doc, user);
}

export function canSeePreviewRemarks(doc, user) {
  if (!doc || !user) return false;
  if (doc.canViewMinutes !== undefined && doc.canViewMinutes !== null) {
    return !!doc.canViewMinutes;
  }
  return Number(doc.currentOwnerUserId) === Number(user.id)
    || hasPermission(user, "VIEW_REMARKS_WHEN_NOT_REPORT_AT");
}
