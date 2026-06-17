import { describe, expect, it } from "vitest";
import { compactRecipientSummary, fullRecipientSummary, recipientIds } from "./recipientSummaryLogic";

describe("recipientSummaryLogic", () => {
  it("builds compact and full To/CC/BCC text with visible recipients", () => {
    const summary = {
      to: [{ userId: 1, name: "Samantha", currentUser: true }],
      cc: [
        { userId: 2, name: "Kamal", currentUser: false },
        { userId: 3, name: "Nimal", currentUser: false },
        { userId: 4, name: "Amal", currentUser: false },
      ],
      bcc: [{ userId: 5, name: "Audit", currentUser: false }],
    };

    expect(compactRecipientSummary(summary)).toBe("To: you • CC: Kamal +2 • BCC: Audit");
    expect(fullRecipientSummary(summary)).toBe("To: you • CC: Kamal, Nimal, Amal • BCC: Audit");
  });

  it("prefers backend compact text when present", () => {
    expect(compactRecipientSummary({ compactText: "To: you • CC: Kamal +1" })).toBe("To: you • CC: Kamal +1");
  });

  it("extracts recipient ids for selected chips", () => {
    expect(recipientIds({ cc: [{ userId: 2 }, { userId: null }, { userId: 3 }] }, "cc")).toEqual([2, 3]);
  });
});
