import { describe, expect, it } from "vitest";
import {
  createApiError,
  getApiErrorMessage,
  isBackendUnavailableError,
  SERVER_UNAVAILABLE_MESSAGE,
} from "./apiClient";

describe("api client error handling", () => {
  it("detects backend-down axios errors without a response", () => {
    const error = { message: "Network Error", request: {}, response: undefined };

    expect(isBackendUnavailableError(error)).toBe(true);
    expect(getApiErrorMessage(error)).toBe(SERVER_UNAVAILABLE_MESSAGE);
  });

  it("preserves status and auth metadata for backend responses", () => {
    const error = {
      response: {
        status: 401,
        data: { message: "Invalid token" },
      },
    };

    const apiError = createApiError(error);

    expect(apiError.message).toBe("Invalid token");
    expect(apiError.status).toBe(401);
    expect(apiError.isAuthError).toBe(true);
    expect(apiError.isBackendUnavailable).toBe(false);
  });

  it("marks network failures as backend unavailable", () => {
    const apiError = createApiError({ code: "ECONNABORTED", request: {} });

    expect(apiError.message).toBe(SERVER_UNAVAILABLE_MESSAGE);
    expect(apiError.status).toBeNull();
    expect(apiError.isNetworkError).toBe(true);
    expect(apiError.isBackendUnavailable).toBe(true);
    expect(apiError.isAuthError).toBe(false);
  });
});
