import { describe, expect, it } from "vitest";
import { formatUserLabel, formatUserLabelById } from "./userLabel";

describe("user label formatting", () => {
  it("shows users as Name (Role) without exposing frontend IDs", () => {
    expect(formatUserLabel({ id: 20, fullName: "Milinda", role: "ASC" })).toBe("Milinda (ASC)");
  });

  it("uses safe fallbacks without exposing IDs", () => {
    expect(formatUserLabel({ id: 22 })).toBe("Unknown user");
    expect(formatUserLabelById(22, [])).toBe("Unknown user");
  });

  it("keeps useful identity details when a role or username is the only available value", () => {
    expect(formatUserLabel({ id: 22, role: "DC" })).toBe("Unknown user (DC)");
    expect(formatUserLabel({ id: 23, username: "prasad.dc" })).toBe("prasad.dc");
  });
});
