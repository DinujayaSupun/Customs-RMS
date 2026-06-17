export function recipientDisplayName(recipient) {
  if (!recipient) return "-";
  if (recipient.currentUser) return "you";
  return recipient.name || "Unknown user";
}

function compactGroup(label, recipients = []) {
  if (!Array.isArray(recipients) || recipients.length === 0) return "";
  const first = recipientDisplayName(recipients[0]);
  const suffix = recipients.length > 1 ? ` +${recipients.length - 1}` : "";
  return `${label}: ${first}${suffix}`;
}

export function compactRecipientSummary(summary) {
  if (!summary) return "";
  if (summary.compactText) return summary.compactText;
  return [
    compactGroup("To", summary.to),
    compactGroup("CC", summary.cc),
    compactGroup("BCC", summary.bcc),
  ].filter(Boolean).join(" • ");
}

function fullGroup(label, recipients = []) {
  if (!Array.isArray(recipients) || recipients.length === 0) return "";
  return `${label}: ${recipients.map(recipientDisplayName).join(", ")}`;
}

export function fullRecipientSummary(summary) {
  if (!summary) return "";
  return [
    fullGroup("To", summary.to),
    fullGroup("CC", summary.cc),
    fullGroup("BCC", summary.bcc),
  ].filter(Boolean).join(" • ");
}

export function recipientIds(summary, key) {
  const list = summary?.[key];
  if (!Array.isArray(list)) return [];
  return list.map((recipient) => recipient.userId).filter((id) => id !== null && id !== undefined);
}
