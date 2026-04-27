import { describe, expect, it } from "vitest";
import {
  canPreviewDocument,
  canSeePreviewOperational,
  canSeePreviewRemarks,
  getDocumentsPageDaysOpenDisplay,
} from "./documentsPageLogic";

describe("documentsPageLogic", () => {
  it("allows preview for report-at owner and for users with view-all-history", () => {
    const doc = { currentOwnerUserId: 55 };

    expect(canPreviewDocument(doc, { id: 55, permissions: [] })).toBe(true);
    expect(canPreviewDocument(doc, { id: 99, permissions: ["VIEW_ALL_HISTORY"] })).toBe(true);
    expect(canPreviewDocument(doc, { id: 99, permissions: [] })).toBe(false);
  });

  it("keeps preview operational details aligned with preview permission", () => {
    const doc = { currentOwnerUserId: 10 };

    expect(canSeePreviewOperational(doc, { id: 10, permissions: [] })).toBe(true);
    expect(canSeePreviewOperational(doc, { id: 11, permissions: [] })).toBe(false);
  });

  it("lets preview remarks be seen by owner or users with the dedicated remarks permission", () => {
    const doc = { currentOwnerUserId: 10 };

    expect(canSeePreviewRemarks(doc, { id: 10, permissions: [] })).toBe(true);
    expect(canSeePreviewRemarks(doc, { id: 11, permissions: ["VIEW_REMARKS_WHEN_NOT_REPORT_AT"] })).toBe(true);
    expect(canSeePreviewRemarks(doc, { id: 11, permissions: [] })).toBe(false);
  });

  it("shows Closed for done documents and numeric days for active ones", () => {
    expect(getDocumentsPageDaysOpenDisplay({ status: "ISSUED", receivedDate: "2026-04-01" })).toBe("Closed");
    expect(
      getDocumentsPageDaysOpenDisplay(
        { status: "PENDING", receivedDate: "2026-04-20T00:00:00Z" },
        Date.parse("2026-04-27T00:00:00Z"),
      ),
    ).toBe(7);
  });
});
