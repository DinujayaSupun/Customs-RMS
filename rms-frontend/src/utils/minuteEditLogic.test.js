import { describe, expect, it } from "vitest";
import { canDeleteMinute, canEditMinute, getEditableMinuteText } from "./minuteEditLogic";

describe("minuteEditLogic", () => {
  it("allows editing and deleting only when backend marks the minute as allowed", () => {
    expect(canEditMinute({ canEdit: true })).toBe(true);
    expect(canDeleteMinute({ canDelete: true })).toBe(true);
    expect(canEditMinute({ canEdit: false })).toBe(false);
    expect(canDeleteMinute({ canDelete: false })).toBe(false);
  });

  it("trims editable minute text before saving", () => {
    expect(getEditableMinuteText("  updated minute  ")).toBe("updated minute");
  });
});
