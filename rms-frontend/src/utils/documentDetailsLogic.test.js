import { describe, expect, it } from "vitest";
import {
  buildMovementRemarksMap,
  getDocumentDetailsCapabilities,
  getAvailableForwardVisibilities,
  getDaysOpenDisplay,
} from "./documentDetailsLogic";

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
    // An APPROVED document is already decided, so reject is not offered (only reopen/issue) —
    // this mirrors the backend, which blocks rejecting an approved document.
    expect(capabilities.canReject).toBe(false);
    expect(capabilities.canIssue).toBe(true);
    expect(capabilities.canReopen).toBe(true);
    expect(capabilities.canUploadAttachments).toBe(true);
    expect(capabilities.canEditDetails).toBe(false);
    expect(capabilities.canTypeRemark).toBe(true);
  });

  it("hides both approve and reject once a document is decided (approved or rejected)", () => {
    const perms = user(7, ["APPROVE_DOCUMENT", "REJECT_DOCUMENT", "REOPEN_DOCUMENT"]);
    const base = {
      currentOwnerUserId: 7,
      receivedDate: "2026-04-20",
      issuedAt: null,
    };

    const rejected = getDocumentDetailsCapabilities({
      doc: { ...base, status: "REJECTED", completedAt: "2026-04-24T10:00:00Z" },
      user: perms,
      approveRejectButtonsEnabled: true,
      forwardReturnAllowedStatuses: ["PENDING", "IN_PROGRESS", "RETURNED"],
    });
    // A rejected document must not offer Approve (backend blocks it) — only Reopen.
    expect(rejected.canApprove).toBe(false);
    expect(rejected.canReject).toBe(false);
    expect(rejected.canReopen).toBe(true);

    const approved = getDocumentDetailsCapabilities({
      doc: { ...base, status: "APPROVED", completedAt: "2026-04-24T10:00:00Z" },
      user: perms,
      approveRejectButtonsEnabled: true,
      forwardReturnAllowedStatuses: ["PENDING", "IN_PROGRESS", "RETURNED"],
    });
    // An approved document must not offer Reject (backend blocks it) — only Reopen/Issue.
    expect(approved.canApprove).toBe(false);
    expect(approved.canReject).toBe(false);
    expect(approved.canReopen).toBe(true);
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

  it("allows Done before approval when approve/reject buttons are disabled", () => {
    const pendingDoc = {
      currentOwnerUserId: 7,
      status: "PENDING",
      receivedDate: "2026-04-20",
      completedAt: null,
      issuedAt: null,
    };

    const ownerWithDone = user(7, ["ISSUE_DOCUMENT"]);

    expect(
      getDocumentDetailsCapabilities({
        doc: pendingDoc,
        user: ownerWithDone,
        approveRejectButtonsEnabled: true,
        forwardReturnAllowedStatuses: ["PENDING", "IN_PROGRESS", "RETURNED"],
      }).canIssue,
    ).toBe(false);

    expect(
      getDocumentDetailsCapabilities({
        doc: pendingDoc,
        user: ownerWithDone,
        approveRejectButtonsEnabled: false,
        forwardReturnAllowedStatuses: ["PENDING", "IN_PROGRESS", "RETURNED"],
      }).canIssue,
    ).toBe(true);
  });

  it("exposes canWorkflow true when any single workflow action is available", () => {
    const baseDoc = {
      currentOwnerUserId: 7,
      status: "IN_PROGRESS",
      receivedDate: "2026-04-20",
      completedAt: null,
      issuedAt: null,
    };

    expect(
      getDocumentDetailsCapabilities({
        doc: baseDoc,
        user: user(7, ["FORWARD_DOCUMENT", "FORWARD_PUBLIC"]),
        approveRejectButtonsEnabled: true,
        forwardReturnAllowedStatuses: ["IN_PROGRESS"],
      }).canWorkflow,
    ).toBe(true);

    expect(
      getDocumentDetailsCapabilities({
        doc: baseDoc,
        user: user(7, ["APPROVE_DOCUMENT"]),
        approveRejectButtonsEnabled: true,
        forwardReturnAllowedStatuses: [],
      }).canWorkflow,
    ).toBe(true);
  });

  it("exposes canWorkflow false for a non-owner viewer with no workflow permissions", () => {
    expect(
      getDocumentDetailsCapabilities({
        doc: {
          currentOwnerUserId: 99,
          status: "IN_PROGRESS",
          receivedDate: "2026-04-20",
          completedAt: null,
          issuedAt: null,
        },
        user: user(7, ["VIEW_REMARKS_WHEN_NOT_REPORT_AT"]),
        approveRejectButtonsEnabled: true,
        forwardReturnAllowedStatuses: ["IN_PROGRESS"],
      }).canWorkflow,
    ).toBe(false);
  });

  it("matches remarks to the nearest later movement by the same user within the allowed window", () => {
    const movements = [
      { id: 1, actionByUserId: 7, actionAt: "2026-06-15T10:05:00Z" },
      { id: 2, actionByUserId: 8, actionAt: "2026-06-15T10:06:00Z" },
      { id: 3, actionByUserId: 7, actionAt: "2026-06-15T10:08:00Z" },
      { id: 4, actionByUserId: 7, actionAt: "2026-06-15T10:40:00Z" },
    ];
    const remarks = [
      { id: 10, remarkedByUserId: 7, remarkedAt: "2026-06-15T10:00:00Z", remarkText: "nearest later movement" },
      { id: 11, remarkedByUserId: 8, remarkedAt: "2026-06-15T10:00:00Z", remarkText: "different user" },
      { id: 12, remarkedByUserId: 7, remarkedAt: "2026-06-15T10:20:00Z", remarkText: "outside window" },
    ];

    const map = buildMovementRemarksMap(movements, remarks);

    expect(map.get(1)).toEqual([remarks[0]]);
    expect(map.get(2)).toEqual([remarks[1]]);
    expect(map.get(3)).toEqual([]);
    expect(map.get(4)).toEqual([]);
  });
});
