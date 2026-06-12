import { createAuthedHttp, getApiErrorMessage } from "./apiClient";

const http = createAuthedHttp();

function getMsg(e) {
  return getApiErrorMessage(e);
}

/**
 * Your backend uses /api/documents (confirmed by controllers in your zip).
 * So we keep it clean and stable.
 */
const BASE = "/documents";

// ===================== DOCUMENTS =====================
export async function listDocuments({ page = 0, size = 100, search } = {}) {
  try {
    return (await http.get(BASE, {
      params: {
        page,
        size,
        ...(search ? { search } : {}),
      },
    })).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function listMyInboxDocuments({ page = 0, size = 200 } = {}) {
  try {
    return (await http.get(`${BASE}/my-inbox`, {
      params: { page, size },
    })).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function getMyWorkloadStats() {
  try {
    return (await http.get(`${BASE}/my-workload-stats`)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function getWorkflowRules() {
  try {
    return (await http.get("/workflow-rules")).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function listSentMessages(params = {}) {
  try {
    return (await http.get(`${BASE}/sent-messages`, {
      params: {
        page: params.page ?? 0,
        size: params.size ?? 200,
        search: params.search ?? undefined,
      },
    })).data;
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

export async function updateDocument(id, payload) {
  try {
    return (await http.put(`${BASE}/${id}`, payload)).data;
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

export async function deleteDocument(id) {
  try {
    return (await http.delete(`${BASE}/${id}`)).data;
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

export async function undoSendDocument(documentId, payload = {}) {
  try {
    return (await http.post(`${BASE}/${documentId}/undo-send`, payload)).data;
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

export async function updateRemark(documentId, remarkId, payload) {
  try {
    return (await http.put(`${BASE}/${documentId}/remarks/${remarkId}`, payload)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function deleteRemark(documentId, remarkId) {
  try {
    return (await http.delete(`${BASE}/${documentId}/remarks/${remarkId}`)).data;
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

export async function uploadAttachment(documentId, file) {
  try {
    const form = new FormData();
    form.append("file", file);

    return (
      await http.post(`${BASE}/${documentId}/attachments`, form, {
        headers: { "Content-Type": "multipart/form-data" },
      })
    ).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function deleteAttachment(attachmentId) {
  try {
    return (await http.delete(`/attachments/${attachmentId}`)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function createAttachmentDownloadUrl(attachmentId, { inline = false } = {}) {
  try {
    const data = (await http.post(`/attachments/${attachmentId}/download-token`, null, {
      params: { inline },
    })).data;
    return String(data?.url || "");
  } catch (e) {
    throw new Error(getMsg(e));
  }
}
