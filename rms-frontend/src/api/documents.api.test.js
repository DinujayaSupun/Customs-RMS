import { beforeEach, describe, expect, it, vi } from "vitest";

const http = vi.hoisted(() => ({
  post: vi.fn(),
}));

vi.mock("./apiClient", () => ({
  createAuthedHttp: () => http,
  getApiErrorMessage: (error) => error?.message || "Request failed",
}));

import { createAttachmentDownloadUrl } from "./documents.api";

describe("documents api", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("creates attachment URLs from the scoped download-token endpoint", async () => {
    http.post.mockResolvedValue({
      data: {
        url: "http://localhost:8080/api/attachments/42/download?download_token=attachment-token&inline=true",
      },
    });

    const url = await createAttachmentDownloadUrl(42, { inline: true });
    const parsed = new URL(url);

    expect(http.post).toHaveBeenCalledWith("/attachments/42/download-token", null, {
      params: { inline: true },
    });
    expect(parsed.pathname).toBe("/api/attachments/42/download");
    expect(parsed.searchParams.get("download_token")).toBe("attachment-token");
    expect(parsed.searchParams.get("access_token")).toBeNull();
    expect(parsed.searchParams.get("inline")).toBe("true");
  });
});
