export const DOCUMENT_PRIORITY_ORDER = { LOW: 1, MEDIUM: 2, HIGH: 3, URGENT: 4 };
export const DOCUMENT_STATUS_ORDER = { PENDING: 1, IN_PROGRESS: 2, APPROVED: 3, ISSUED: 4, REJECTED: 5 };

function toText(value) {
  return String(value ?? "").trim().toLowerCase();
}

export function toDocumentRecentScore(doc) {
  const source = doc?.updatedAt ?? doc?.createdAt ?? doc?.receivedDate;
  const parsed = Date.parse(source);
  if (!Number.isNaN(parsed)) return parsed;
  const idNumber = Number(doc?.id);
  return Number.isFinite(idNumber) ? idNumber : 0;
}

export function sortDocumentsBy(list, sortBy, getDaysOpenScore = () => 0) {
  const arr = [...list];
  switch (sortBy) {
    case "ref_asc":
      return arr.sort((a, b) => toText(a.refNo).localeCompare(toText(b.refNo)));
    case "ref_desc":
      return arr.sort((a, b) => toText(b.refNo).localeCompare(toText(a.refNo)));
    case "title_asc":
      return arr.sort((a, b) => toText(a.title).localeCompare(toText(b.title)));
    case "days_open_desc":
      return arr.sort((a, b) => getDaysOpenScore(b) - getDaysOpenScore(a));
    case "days_open_asc":
      return arr.sort((a, b) => getDaysOpenScore(a) - getDaysOpenScore(b));
    case "priority_desc":
      return arr.sort((a, b) => (DOCUMENT_PRIORITY_ORDER[b.priority] ?? 0) - (DOCUMENT_PRIORITY_ORDER[a.priority] ?? 0));
    case "status_asc":
      return arr.sort((a, b) => (DOCUMENT_STATUS_ORDER[a.status] ?? 999) - (DOCUMENT_STATUS_ORDER[b.status] ?? 999));
    case "recent":
    default:
      return arr.sort((a, b) => toDocumentRecentScore(b) - toDocumentRecentScore(a));
  }
}

export function matchesReceivedDateRange(doc, receivedFrom, receivedTo) {
  const received = String(doc?.receivedDate ?? "");
  if (!received) return false;
  if (receivedFrom && received < receivedFrom) return false;
  if (receivedTo && received > receivedTo) return false;
  return true;
}
