import { describe, expect, it } from "vitest";
import { getWorkflowSenderSuccessMessage } from "./workflowNotificationLogic";

describe("workflowNotificationLogic", () => {
  it("builds sender success messages only after a workflow action succeeds", () => {
    expect(getWorkflowSenderSuccessMessage("FORWARD")).toBe("Document sent successfully.");
    expect(getWorkflowSenderSuccessMessage("RETURN")).toBe("Document returned successfully.");
  });

  it("falls back to a generic success message for unknown workflow actions", () => {
    expect(getWorkflowSenderSuccessMessage("APPROVE")).toBe("Document action completed successfully.");
  });
});
