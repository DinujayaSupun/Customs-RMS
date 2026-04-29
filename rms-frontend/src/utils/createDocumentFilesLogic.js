export function getFileKey(file) {
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

export async function uploadFilesInSelectedOrder(documentId, files, uploadAttachment) {
  for (const file of files || []) {
    await uploadAttachment(documentId, file);
  }
}
