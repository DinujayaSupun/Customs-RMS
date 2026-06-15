import { describe, expect, it } from "vitest";
import { shouldClearSessionForAuthCheckError } from "./authGuardLogic";

describe("auth guard backend-down behavior", () => {
  it("does not clear the session when the backend is unavailable", () => {
    expect(shouldClearSessionForAuthCheckError({ isBackendUnavailable: true })).toBe(false);
  });

  it("clears the session for real authentication failures", () => {
    expect(shouldClearSessionForAuthCheckError({ status: 401 })).toBe(true);
    expect(shouldClearSessionForAuthCheckError({ isAuthError: true })).toBe(true);
  });
});
