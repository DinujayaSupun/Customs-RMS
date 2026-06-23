import { describe, expect, it } from "vitest";
import { getDocumentNotificationMessage, isDocumentNotification } from "./realtimeNotificationLogic";

describe("realtimeNotificationLogic", () => {
  it("treats forwarded, returned and copied document messages as document notifications", () => {
    expect(isDocumentNotification({ type: "DOCUMENT_FORWARDED" })).toBe(true);
    expect(isDocumentNotification({ type: "DOCUMENT_RETURNED" })).toBe(true);
    expect(isDocumentNotification({ type: "DOCUMENT_COPIED" })).toBe(true);
    expect(isDocumentNotification({ type: "PERMISSIONS_UPDATED" })).toBe(false);
  });

  it("uses backend message text with a safe fallback", () => {
    expect(getDocumentNotificationMessage({ message: "Document returned: REF-1" })).toBe("Document returned: REF-1");
    expect(getDocumentNotificationMessage({})).toBe("A document has been assigned to you.");
  });
});
