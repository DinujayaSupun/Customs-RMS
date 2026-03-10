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

export async function listAuditLogs(params = {}) {
  try {
    return (await http.get("/audit-logs", { params })).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export function buildAuditLogsExportUrl(params = {}) {
  const url = new URL("http://localhost:8080/api/audit-logs/export");

  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && String(value).trim() !== "") {
      url.searchParams.set(key, String(value));
    }
  }

  const token = getAccessToken();
  if (token) url.searchParams.set("access_token", token);

  return url.toString();
}
