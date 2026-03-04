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
