export function getFileKey(file) {
  // Browser File objects do not have stable ids, so this key prevents duplicate selections.
  return [
    file?.name || "",
    file?.size || 0,
    file?.lastModified || 0,
  ].join("::");
}

export function addSelectedFiles(currentFiles, filesToAdd) {
  const next = Array.isArray(currentFiles) ? [...currentFiles] : [];
  const seen = new Set(next.map(getFileKey));

  for (const file of Array.from(filesToAdd || [])) {
    const key = getFileKey(file);
    if (!seen.has(key)) {
      next.push(file);
      seen.add(key);
    }
  }

  return next;
}

export function removeSelectedFile(currentFiles, fileToRemove) {
  const removeKey = getFileKey(fileToRemove);
  return (currentFiles || []).filter((file) => getFileKey(file) !== removeKey);
}

export function getSelectedFileRole(index) {
  return index === 0 ? "Main file" : "Attachment";
}

export function formatVersionedFileName(versionNo, fileName) {
  const version = Number(versionNo);
  const safeVersion = Number.isFinite(version) && version > 0 ? version : 1;
  const safeFileName = String(fileName || "file").trim() || "file";
  return `V${safeVersion} - ${safeFileName}`;
}

export function getSelectedFileVersionLabel(index, fileName) {
  return formatVersionedFileName(Number(index) + 1, fileName);
}

export function getSelectedFilesSummary(files) {
  const selected = Array.from(files || []);
  if (selected.length === 0) return "No files chosen";

  const countLabel = selected.length === 1 ? "1 file selected" : `${selected.length} files selected`;
  const names = selected
    .slice(0, 2)
    .map((file, index) => getSelectedFileVersionLabel(index, file?.name));
  const remaining = selected.length - names.length;

  return remaining > 0
    ? `${countLabel}: ${names.join(", ")} + ${remaining} more`
    : `${countLabel}: ${names.join(", ")}`;
}

export async function uploadFilesInSelectedOrder(documentId, files, uploadAttachment) {
  // Upload order matters: the backend assigns V1/Main to the first file it receives.
  for (const file of files || []) {
    await uploadAttachment(documentId, file);
  }
}
