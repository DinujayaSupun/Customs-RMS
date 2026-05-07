import { describe, expect, it } from "vitest";
import { matchesReceivedDateRange, sortDocumentsBy, toDocumentRecentScore } from "./documentsLogic";

describe("documentsLogic", () => {
  it("prefers updatedAt over an older receivedDate when sorting by recent", () => {
    const docs = [
      {
        id: 2,
        refNo: "B",
        updatedAt: null,
        createdAt: "2026-04-21T10:00:00Z",
        receivedDate: "2026-04-26",
      },
      {
        id: 1,
        refNo: "A",
        updatedAt: "2026-04-27T10:00:00Z",
        createdAt: "2026-04-20T10:00:00Z",
        receivedDate: "2026-01-01",
      },
    ];

    const sorted = sortDocumentsBy(docs, "recent");
    expect(sorted.map((doc) => doc.id)).toEqual([1, 2]);
  });

  it("falls back to createdAt before receivedDate", () => {
    const doc = {
      id: 5,
      updatedAt: null,
      createdAt: "2026-04-25T12:00:00Z",
      receivedDate: "2026-01-01",
    };

    expect(toDocumentRecentScore(doc)).toBe(Date.parse("2026-04-25T12:00:00Z"));
  });

  it("matches received date ranges inclusively", () => {
    const doc = { receivedDate: "2026-04-27" };

    expect(matchesReceivedDateRange(doc, "2026-04-27", "2026-04-27")).toBe(true);
    expect(matchesReceivedDateRange(doc, "2026-04-28", "")).toBe(false);
    expect(matchesReceivedDateRange(doc, "", "2026-04-26")).toBe(false);
  });
});
