const DOCUMENT_NOTIFICATION_TYPES = new Set([
  "DOCUMENT_FORWARDED",
  "DOCUMENT_RETURNED",
]);

export function isDocumentNotification(payload) {
  return DOCUMENT_NOTIFICATION_TYPES.has(String(payload?.type || "").toUpperCase());
}

export function getDocumentNotificationMessage(payload) {
  return payload?.message || "A document has been assigned to you.";
}
