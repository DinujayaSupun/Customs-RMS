import axios from "axios";
import { getAccessToken } from "../auth/currentUser";
import { setBackendStatus } from "../services/backendStatus";

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
export const SERVER_UNAVAILABLE_MESSAGE = "Server is currently unavailable. Please try again shortly.";

export function isBackendUnavailableError(error) {
  if (!error || error.response) return false;
  return !!error.request
    || error.code === "ECONNABORTED"
    || error.code === "ERR_NETWORK"
    || String(error.message || "").toLowerCase().includes("network error");
}

export function createApiError(error, { includeDetails = false } = {}) {
  const status = error?.response?.status ?? null;
  const isNetworkError = isBackendUnavailableError(error);
  const apiError = new Error(getApiErrorMessage(error, { includeDetails }));
  apiError.cause = error;
  apiError.status = status;
  apiError.isNetworkError = isNetworkError;
  apiError.isBackendUnavailable = isNetworkError;
  apiError.isAuthError = status === 401 || status === 403;
  return apiError;
}

export function createAuthedHttp() {
  const http = axios.create({
    baseURL: `${API_BASE_URL}/api`,
    timeout: 20000,
  });

  // Attach the latest token at request time so profile/session updates do not require recreating clients.
  http.interceptors.request.use((config) => {
    const token = getAccessToken();
    if (token) {
      config.headers = config.headers || {};
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });

  http.interceptors.response.use(
    (response) => {
      setBackendStatus("available");
      return response;
    },
    (error) => {
      if (isBackendUnavailableError(error)) {
        setBackendStatus("unavailable");
      }
      return Promise.reject(error);
    }
  );

  return http;
}

export function getApiErrorMessage(error, { includeDetails = false } = {}) {
  if (isBackendUnavailableError(error)) {
    return SERVER_UNAVAILABLE_MESSAGE;
  }

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
