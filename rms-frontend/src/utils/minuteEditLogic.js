export function canEditMinute(minute) {
  return minute?.canEdit === true;
}

export function canDeleteMinute(minute) {
  return minute?.canDelete === true;
}

export function getEditableMinuteText(value) {
  return String(value || "").trim();
}
