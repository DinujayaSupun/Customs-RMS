import axios from "axios";
import { getAccessToken } from "../auth/currentUser";

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

export function createAuthedHttp() {
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

  return http;
}

export function getApiErrorMessage(error, { includeDetails = false } = {}) {
  const details = error?.response?.data?.details;
  if (includeDetails && Array.isArray(details) && details.length > 0) {
    return details.join(" ");
  }

  return (
    error?.response?.data?.message ||
    error?.response?.data?.error ||
    error?.message ||
    "Request failed"
  );
}
