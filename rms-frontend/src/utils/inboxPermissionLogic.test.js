import { describe, expect, it } from "vitest";
import { canForwardInboxDocument, canReturnInboxDocument } from "./inboxPermissionLogic";

describe("inboxPermissionLogic", () => {
  it("allows forward only in received mode for the current owner with a valid status and visibility permission", () => {
    const doc = { currentOwnerUserId: 42, status: "IN_PROGRESS" };
    const user = { id: 42, permissions: ["FORWARD_DOCUMENT"] };

    expect(
      canForwardInboxDocument({
        doc,
        user,
        inboxMode: "received",
        forwardReturnAllowedStatuses: ["PENDING", "IN_PROGRESS", "RETURNED"],
        availableForwardVisibilities: ["PUBLIC"],
      }),
    ).toBe(true);

    expect(
      canForwardInboxDocument({
        doc,
        user,
        inboxMode: "sent",
        forwardReturnAllowedStatuses: ["PENDING", "IN_PROGRESS", "RETURNED"],
        availableForwardVisibilities: ["PUBLIC"],
      }),
    ).toBe(false);
  });

  it("blocks forward when status is not allowed or no visibility option is available", () => {
    const doc = { currentOwnerUserId: 42, status: "APPROVED" };
    const user = { id: 42, permissions: ["FORWARD_DOCUMENT"] };

    expect(
      canForwardInboxDocument({
        doc,
        user,
        inboxMode: "received",
        forwardReturnAllowedStatuses: ["PENDING", "IN_PROGRESS", "RETURNED"],
        availableForwardVisibilities: ["PUBLIC"],
      }),
    ).toBe(false);

    expect(
      canForwardInboxDocument({
        doc: { currentOwnerUserId: 42, status: "IN_PROGRESS" },
        user,
        inboxMode: "received",
        forwardReturnAllowedStatuses: ["PENDING", "IN_PROGRESS", "RETURNED"],
        availableForwardVisibilities: [],
      }),
    ).toBe(false);
  });

  it("allows return only for the current owner with RETURN_DOCUMENT permission and allowed status", () => {
    expect(
      canReturnInboxDocument({
        doc: { currentOwnerUserId: 42, status: "RETURNED" },
        user: { id: 42, permissions: ["RETURN_DOCUMENT"] },
        forwardReturnAllowedStatuses: ["PENDING", "IN_PROGRESS", "RETURNED"],
      }),
    ).toBe(true);

    expect(
      canReturnInboxDocument({
        doc: { currentOwnerUserId: 42, status: "APPROVED" },
        user: { id: 42, permissions: ["RETURN_DOCUMENT"] },
        forwardReturnAllowedStatuses: ["PENDING", "IN_PROGRESS", "RETURNED"],
      }),
    ).toBe(false);
  });
});
