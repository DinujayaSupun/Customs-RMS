import axios from "axios";
import { getAccessToken } from "../auth/currentUser";

const http = axios.create({
  baseURL: "http://localhost:8080/api",
  timeout: 20000,
});

http.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
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
    return (await http.get(BASE, {
      params: {
        page: 0,
        size: 100,
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

export function buildAttachmentUrl(attachmentId, { inline = false } = {}) {
  const url = new URL(`http://localhost:8080/api/attachments/${attachmentId}/download`);
  const token = getAccessToken();
  if (token) url.searchParams.set("access_token", token);
  if (inline) url.searchParams.set("inline", "true");
  return url.toString();
}