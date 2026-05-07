export function sortAttachmentsByVersion(attachments) {
  if (!Array.isArray(attachments) || attachments.length === 0) return [];
  return [...attachments].sort((a, b) => Number(a?.versionNo) - Number(b?.versionNo));
}

export function getPrimaryAttachment(attachments) {
  const sorted = sortAttachmentsByVersion(attachments);
  if (!sorted.length) return null;
  return sorted.find((item) => Number(item?.versionNo) === 1) || sorted[0];
}

export function getSelectedAttachment(attachments, selectedAttachmentId) {
  const sorted = sortAttachmentsByVersion(attachments);
  if (!sorted.length) return null;

  return (
    sorted.find((item) => Number(item?.id) === Number(selectedAttachmentId))
    || getPrimaryAttachment(sorted)
  );
}

export function getAttachmentViewerState(attachments, selectedAttachmentId) {
  const sortedAttachments = sortAttachmentsByVersion(attachments);
  const primaryAttachment = getPrimaryAttachment(sortedAttachments);
  const selectedAttachment = getSelectedAttachment(sortedAttachments, selectedAttachmentId);
  const selectedIndex = selectedAttachment
    ? sortedAttachments.findIndex((item) => Number(item?.id) === Number(selectedAttachment.id))
    : -1;

  return {
    sortedAttachments,
    primaryAttachment,
    selectedAttachment,
    selectedIndex,
    canGoPrevious: selectedIndex > 0,
    canGoNext: selectedIndex >= 0 && selectedIndex < sortedAttachments.length - 1,
  };
}

export function isPdfAttachmentName(fileName) {
  return String(fileName || "").toLowerCase().endsWith(".pdf");
}

export function isImageAttachmentName(fileName) {
  return /\.(png|jpe?g|gif|webp)$/i.test(String(fileName || ""));
}

export function isPreviewableAttachmentName(fileName) {
  return isPdfAttachmentName(fileName) || isImageAttachmentName(fileName);
}

export function resolveAttachmentTypeFromName(fileName) {
  const lower = String(fileName || "").toLowerCase();
  if (lower.endsWith(".pdf")) return "PDF";
  if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "DOC";
  if (lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv")) return "XLS";
  if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp")) return "IMG";
  if (lower.endsWith(".txt")) return "TXT";
  if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")) return "ZIP";
  return "FILE";
}
