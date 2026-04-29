import { describe, expect, it } from "vitest";
import { formatDateSafe, formatDateTimeSafe } from "./dateFormat";

describe("dateFormat", () => {
  it("returns a dash for empty values", () => {
    expect(formatDateSafe("")).toBe("-");
    expect(formatDateTimeSafe(null)).toBe("-");
  });

  it("returns the original value for invalid dates", () => {
    expect(formatDateSafe("not-a-date")).toBe("not-a-date");
    expect(formatDateTimeSafe("not-a-date")).toBe("not-a-date");
  });

  it("formats valid dates using the current locale", () => {
    const value = "2026-04-28T10:30:00Z";

    expect(formatDateSafe(value)).toBe(new Date(value).toLocaleDateString());
    expect(formatDateTimeSafe(value)).toBe(new Date(value).toLocaleString());
  });
});
