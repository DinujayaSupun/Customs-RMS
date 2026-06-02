import { describe, expect, it } from "vitest";
import { formatUserLabel, formatUserLabelById, formatUserLabelFromParts } from "./userLabel";

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

  it("uses backend-provided names before falling back to the user lookup list", () => {
    expect(formatUserLabelFromParts({ userId: 1, name: "System Administrator" }, [])).toBe("System Administrator");
    expect(formatUserLabelFromParts({ userId: 2 }, [{ id: 2, fullName: "Prasad", role: "DC" }])).toBe("Prasad (DC)");
    expect(formatUserLabelFromParts({ userId: 3 }, [])).toBe("Unknown user");
  });
});
