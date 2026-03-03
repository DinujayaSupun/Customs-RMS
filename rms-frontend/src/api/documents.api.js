import axios from "axios";

const http = axios.create({
  baseURL: "http://localhost:8080/api",
  timeout: 20000,
});

function getMsg(e) {
  return (
    e?.response?.data?.message ||
    e?.response?.data?.error ||
    e?.message ||
    "Request failed"
  );
}

/**
 * Your backend uses /api/documents (confirmed by controllers in your zip).
 * So we keep it clean and stable.
 */
const BASE = "/documents";

// ===================== DOCUMENTS =====================
export async function listDocuments() {
  try {
    return (await http.get(BASE)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function getDocument(id) {
  try {
    return (await http.get(`${BASE}/${id}`)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function createDocument(payload) {
  try {
    return (await http.post(BASE, payload)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

// ===================== MOVEMENTS =====================
export async function listMovements(documentId) {
  try {
    return (await http.get(`${BASE}/${documentId}/movements`)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function forwardDocument(documentId, payload) {
  try {
    return (await http.post(`${BASE}/${documentId}/forward`, payload)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function returnDocument(documentId, payload) {
  try {
    return (await http.post(`${BASE}/${documentId}/return`, payload)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function approveDocument(documentId, payload) {
  try {
    return (await http.post(`${BASE}/${documentId}/approve`, payload)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function rejectDocument(documentId, payload) {
  try {
    return (await http.post(`${BASE}/${documentId}/reject`, payload)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function issueDocument(documentId, payload) {
  try {
    return (await http.post(`${BASE}/${documentId}/issue`, payload)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function reopenDocument(documentId, payload) {
  try {
    return (await http.post(`${BASE}/${documentId}/reopen`, payload)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

// ===================== REMARKS =====================
export async function listRemarks(documentId) {
  try {
    return (await http.get(`${BASE}/${documentId}/remarks`)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function addRemark(documentId, payload) {
  try {
    return (await http.post(`${BASE}/${documentId}/remarks`, payload)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

// ===================== ATTACHMENTS =====================
export async function listAttachments(documentId) {
  try {
    return (await http.get(`${BASE}/${documentId}/attachments`)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function uploadAttachment(documentId, uploadedByUserId, file) {
  try {
    const form = new FormData();
    form.append("file", file);

    // backend expects uploadedByUserId as QUERY PARAM
    return (
      await http.post(`${BASE}/${documentId}/attachments`, form, {
        params: { uploadedByUserId },
        headers: { "Content-Type": "multipart/form-data" },
      })
    ).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function deleteAttachment(attachmentId, performedByUserId) {
  try {
    // backend expects performedByUserId param
    return (
      await http.delete(`/attachments/${attachmentId}`, {
        params: { performedByUserId },
      })
    ).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export function downloadAttachment(attachmentId, performedByUserId = null) {
  const url = new URL(`http://localhost:8080/api/attachments/${attachmentId}/download`);
  if (performedByUserId) url.searchParams.set("performedByUserId", String(performedByUserId));
  window.open(url.toString(), "_blank");
}