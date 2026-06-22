import { describe, expect, it } from "vitest";
import { safeLoginRedirect, shouldClearSessionForAuthCheckError } from "./authGuardLogic";

describe("auth guard backend-down behavior", () => {
  it("does not clear the session when the backend is unavailable", () => {
    expect(shouldClearSessionForAuthCheckError({ isBackendUnavailable: true })).toBe(false);
  });

  it("clears the session for real authentication failures", () => {
    expect(shouldClearSessionForAuthCheckError({ status: 401 })).toBe(true);
    expect(shouldClearSessionForAuthCheckError({ isAuthError: true })).toBe(true);
  });
});

describe("safeLoginRedirect", () => {
  it("keeps same-site absolute paths", () => {
    expect(safeLoginRedirect("/inbox")).toBe("/inbox");
    expect(safeLoginRedirect("/documents/42?tab=remarks")).toBe("/documents/42?tab=remarks");
  });

  it("rejects off-site and malformed targets, falling back to /inbox", () => {
    expect(safeLoginRedirect("https://evil.com")).toBe("/inbox");
    expect(safeLoginRedirect("//evil.com")).toBe("/inbox");
    expect(safeLoginRedirect("/\\evil.com")).toBe("/inbox");
    expect(safeLoginRedirect("javascript:alert(1)")).toBe("/inbox");
    expect(safeLoginRedirect("")).toBe("/inbox");
    expect(safeLoginRedirect(null)).toBe("/inbox");
  });
});
