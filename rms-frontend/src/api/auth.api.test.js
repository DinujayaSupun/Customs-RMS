import { beforeEach, describe, expect, it, vi } from "vitest";

const http = vi.hoisted(() => ({
  post: vi.fn(),
}));

vi.mock("./apiClient", () => ({
  createAuthedHttp: () => http,
  createApiError: (error) => Object.assign(new Error(error?.message || "Request failed"), error),
  getApiErrorMessage: (error) => error?.message || "Request failed",
}));

import { createMyProfilePictureUrl, getMe } from "./auth.api";

describe("auth api", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("creates profile picture URLs from the scoped download-token endpoint", async () => {
    http.post.mockResolvedValue({
      data: {
        url: "http://localhost:8080/api/auth/me/profile-picture?download_token=profile-token",
      },
    });

    const url = await createMyProfilePictureUrl("2026-06-11T10:00:00");
    const parsed = new URL(url);

    expect(http.post).toHaveBeenCalledWith("/auth/me/profile-picture-token");
    expect(parsed.pathname).toBe("/api/auth/me/profile-picture");
    expect(parsed.searchParams.get("download_token")).toBe("profile-token");
    expect(parsed.searchParams.get("access_token")).toBeNull();
    expect(parsed.searchParams.get("v")).toBe("2026-06-11T10:00:00");
  });

  it("preserves backend-unavailable metadata from failed auth requests", async () => {
    http.get = vi.fn().mockRejectedValue({
      message: "Server is currently unavailable. Please try again shortly.",
      isBackendUnavailable: true,
    });

    await expect(getMe()).rejects.toMatchObject({
      message: "Server is currently unavailable. Please try again shortly.",
      isBackendUnavailable: true,
    });
  });
});
