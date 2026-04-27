import { describe, expect, it } from "vitest";
import { getDocumentDetailsCapabilities, getAvailableForwardVisibilities, getDaysOpenDisplay } from "./documentDetailsLogic";

function user(id, permissions) {
  return { id, permissions };
}

describe("documentDetailsLogic", () => {
  it("shows Closed for issued documents and day count for active ones", () => {
    expect(getDaysOpenDisplay({ status: "ISSUED", receivedDate: "2026-04-01" })).toBe("Closed");
    expect(
      getDaysOpenDisplay(
        { status: "IN_PROGRESS", receivedDate: "2026-04-20T00:00:00Z" },
        Date.parse("2026-04-27T00:00:00Z"),
      ),
    ).toBe("7");
  });

  it("builds available forward visibilities from the exact forward permissions", () => {
    expect(getAvailableForwardVisibilities(user(10, ["FORWARD_PUBLIC"]))).toEqual(["PUBLIC"]);
    expect(getAvailableForwardVisibilities(user(10, ["FORWARD_PRIVATE"]))).toEqual(["PRIVATE"]);
    expect(getAvailableForwardVisibilities(user(10, ["FORWARD_PUBLIC", "FORWARD_PRIVATE"]))).toEqual(["PUBLIC", "PRIVATE"]);
  });

  it("enables owner workflow actions only when the matching permissions and status rules allow them", () => {
    const capabilities = getDocumentDetailsCapabilities({
      doc: {
        currentOwnerUserId: 7,
        status: "APPROVED",
        receivedDate: "2026-04-20",
        completedAt: "2026-04-24T10:00:00Z",
        issuedAt: null,
      },
      user: user(7, [
        "FORWARD_DOCUMENT",
        "FORWARD_PUBLIC",
        "RETURN_DOCUMENT",
        "APPROVE_DOCUMENT",
        "REJECT_DOCUMENT",
        "ISSUE_DOCUMENT",
        "REOPEN_DOCUMENT",
        "UPLOAD_ATTACHMENT",
        "ADD_REMARK",
        "EDIT_DOCUMENT_DETAILS",
      ]),
      approveRejectButtonsEnabled: true,
      forwardReturnAllowedStatuses: ["PENDING", "IN_PROGRESS", "RETURNED"],
    });

    expect(capabilities.canForward).toBe(false);
    expect(capabilities.canReturn).toBe(false);
    expect(capabilities.canApprove).toBe(false);
    expect(capabilities.canReject).toBe(true);
    expect(capabilities.canIssue).toBe(true);
    expect(capabilities.canReopen).toBe(true);
    expect(capabilities.canUploadAttachments).toBe(true);
    expect(capabilities.canEditDetails).toBe(false);
    expect(capabilities.canTypeRemark).toBe(true);
  });

  it("lets non-owners view remarks only with the dedicated permission and blocks acting", () => {
    const capabilities = getDocumentDetailsCapabilities({
      doc: {
        currentOwnerUserId: 99,
        status: "IN_PROGRESS",
        receivedDate: "2026-04-20",
        completedAt: null,
        issuedAt: null,
      },
      user: user(7, ["VIEW_REMARKS_WHEN_NOT_REPORT_AT", "FORWARD_DOCUMENT", "FORWARD_PUBLIC"]),
      approveRejectButtonsEnabled: true,
      forwardReturnAllowedStatuses: ["PENDING", "IN_PROGRESS", "RETURNED"],
    });

    expect(capabilities.canViewRemarks).toBe(true);
    expect(capabilities.canViewHistory).toBe(false);
    expect(capabilities.canForward).toBe(false);
    expect(capabilities.canReturn).toBe(false);
    expect(capabilities.canTypeRemark).toBe(false);
  });

  it("allows reopen of done documents only when approve/reject buttons are disabled", () => {
    const doneDoc = {
      currentOwnerUserId: 7,
      status: "ISSUED",
      receivedDate: "2026-04-20",
      completedAt: "2026-04-24T10:00:00Z",
      issuedAt: "2026-04-24T11:00:00Z",
    };

    const userWithReopen = user(7, ["REOPEN_DOCUMENT"]);

    expect(
      getDocumentDetailsCapabilities({
        doc: doneDoc,
        user: userWithReopen,
        approveRejectButtonsEnabled: true,
        forwardReturnAllowedStatuses: ["PENDING", "IN_PROGRESS", "RETURNED"],
      }).canReopen,
    ).toBe(false);

    expect(
      getDocumentDetailsCapabilities({
        doc: doneDoc,
        user: userWithReopen,
        approveRejectButtonsEnabled: false,
        forwardReturnAllowedStatuses: ["PENDING", "IN_PROGRESS", "RETURNED"],
      }).canReopen,
    ).toBe(true);
  });
});
