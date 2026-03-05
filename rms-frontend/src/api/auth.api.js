import axios from "axios";
import { getAccessToken } from "../auth/currentUser";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "/api";

const http = axios.create({
  baseURL: API_BASE_URL,
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

export async function login(username, password) {
  try {
    return (await http.post("/auth/login", { username, password })).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function getMe() {
  try {
    return (await http.get("/auth/me")).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function listUsers() {
  try {
    return (await http.get("/auth/users")).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function adminListUsers(params = {}) {
  try {
    return (await http.get("/admin/users", { params })).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function adminListRoles() {
  try {
    return (await http.get("/admin/users/roles")).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function adminCreateUser(payload) {
  try {
    return (await http.post("/admin/users", payload)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function adminUpdateUser(userId, payload) {
  try {
    return (await http.put(`/admin/users/${userId}`, payload)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function adminActivateUser(userId) {
  try {
    return (await http.patch(`/admin/users/${userId}/activate`)).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function adminDeactivateUser(userId, fallbackDcUserId) {
  try {
    return (await http.patch(`/admin/users/${userId}/deactivate`, { fallbackDcUserId })).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function adminResetPassword(userId, newPassword) {
  try {
    await http.patch(`/admin/users/${userId}/reset-password`, { newPassword });
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function adminListDuplicateUsers() {
  try {
    return (await http.get("/admin/users/duplicates")).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function adminMergeUsers(sourceUserId, targetUserId) {
  try {
    await http.post("/admin/users/merge", { sourceUserId, targetUserId });
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export function adminExportUsersUrl({ search = "", role = "", active = "" } = {}) {
  const base = API_BASE_URL.startsWith("http") ? API_BASE_URL : window.location.origin;
  const path = API_BASE_URL.startsWith("http")
    ? `${API_BASE_URL.replace(/\/$/, "")}/admin/users/export`
    : `${API_BASE_URL}/admin/users/export`;
  const url = new URL(path, base);
  if (search) url.searchParams.set("search", search);
  if (role) url.searchParams.set("role", role);
  if (active !== "" && active !== null && active !== undefined) {
    url.searchParams.set("active", String(active));
  }
  const token = getAccessToken();
  if (token) url.searchParams.set("access_token", token);
  return url.toString();
}
