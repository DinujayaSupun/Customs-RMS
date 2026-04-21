import axios from "axios";
import { getAccessToken } from "../auth/currentUser";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "");

const http = axios.create({
  baseURL: `${API_BASE_URL}/api`,
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

export async function listAuditLogs(params = {}) {
  try {
    return (await http.get("/audit-logs", { params })).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function getAuditLogFilterOptions() {
  try {
    return (await http.get("/audit-logs/filter-options")).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function exportAuditLogsCsv(params = {}) {
  try {
    const response = await http.get("/audit-logs/export", {
      params,
      responseType: "blob",
    });

    const disposition = response.headers?.["content-disposition"] || "";
    const match = disposition.match(/filename="?([^"]+)"?/i);
    const fileName = match?.[1] || "audit-logs.csv";

    return {
      blob: response.data,
      fileName,
    };
  } catch (e) {
    throw new Error(getMsg(e));
  }
}
