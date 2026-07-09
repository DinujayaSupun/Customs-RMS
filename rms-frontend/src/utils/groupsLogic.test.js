import { describe, expect, it } from "vitest";
import {
  buildForwardPayload,
  buildGroupMembersPayload,
  countAdmins,
  filterGroupsBySearch,
  initialsFor,
} from "./groupsLogic";

describe("groupsLogic", () => {
  describe("initialsFor", () => {
    it("takes the first letter of up to two words", () => {
      expect(initialsFor("Clearance Unit A")).toBe("CU");
      expect(initialsFor("Prasad")).toBe("P");
    });

    it("falls back to G for blank or missing names", () => {
      expect(initialsFor("")).toBe("G");
      expect(initialsFor("   ")).toBe("G");
      expect(initialsFor(null)).toBe("G");
      expect(initialsFor(undefined)).toBe("G");
    });
  });

  describe("filterGroupsBySearch", () => {
    const groups = [
      { id: 7, name: "STAT Unit", members: [{ fullName: "Prasad" }, { fullName: "Uthpala" }] },
      { id: 12, name: "Legal Team", members: [{ fullName: "Milinda" }] },
    ];

    it("returns everything when the search term is blank", () => {
      expect(filterGroupsBySearch(groups, "")).toEqual(groups);
      expect(filterGroupsBySearch(groups, "   ")).toEqual(groups);
    });

    it("matches by group name, case-insensitively", () => {
      expect(filterGroupsBySearch(groups, "stat")).toEqual([groups[0]]);
    });

    it("matches by member name", () => {
      expect(filterGroupsBySearch(groups, "milinda")).toEqual([groups[1]]);
    });

    it("matches by group id", () => {
      expect(filterGroupsBySearch(groups, "12")).toEqual([groups[1]]);
    });

    it("returns an empty list when nothing matches", () => {
      expect(filterGroupsBySearch(groups, "nonexistent")).toEqual([]);
    });

    it("tolerates a non-array input", () => {
      expect(filterGroupsBySearch(null, "x")).toEqual([]);
      expect(filterGroupsBySearch(undefined, "")).toEqual([]);
    });
  });

  describe("buildGroupMembersPayload", () => {
    it("resolves each member's admin flag from the flags map", () => {
      expect(buildGroupMembersPayload(["1", "2"], { 1: true, 2: false })).toEqual([
        { userId: 1, isAdmin: true },
        { userId: 2, isAdmin: false },
      ]);
    });

    it("de-duplicates repeated ids, keeping the first occurrence", () => {
      expect(buildGroupMembersPayload(["1", "1", "2"], { 1: true })).toEqual([
        { userId: 1, isAdmin: true },
        { userId: 2, isAdmin: false },
      ]);
    });

    it("treats a missing flag entry as a non-admin", () => {
      expect(buildGroupMembersPayload(["5"], {})).toEqual([{ userId: 5, isAdmin: false }]);
    });
  });

  describe("countAdmins", () => {
    it("counts only members flagged as admin", () => {
      expect(countAdmins(["1", "2", "3"], { 1: true, 2: false, 3: true })).toBe(2);
    });

    it("returns zero when no member is an admin", () => {
      expect(countAdmins(["1", "2"], {})).toBe(0);
    });

    it("counts a de-duplicated admin only once", () => {
      expect(countAdmins(["1", "1"], { 1: true })).toBe(1);
    });
  });

  describe("buildForwardPayload", () => {
    it("builds a person-target payload", () => {
      expect(buildForwardPayload({
        mode: "person",
        toUserId: "42",
        ccUserIds: [1, 2],
        bccUserIds: [3],
        forwardVisibility: "PUBLIC",
        remarkText: "please review",
      })).toEqual({
        toUserId: 42,
        ccUserIds: [1, 2],
        bccUserIds: [3],
        forwardVisibility: "PUBLIC",
        remarkText: "please review",
      });
    });

    it("builds a group-target payload instead of a person one", () => {
      const payload = buildForwardPayload({
        mode: "group",
        toUserId: "42",
        toGroupId: "7",
        ccUserIds: [],
        bccUserIds: [],
        forwardVisibility: "PRIVATE",
        remarkText: null,
      });
      expect(payload).toEqual({
        toGroupId: 7,
        ccUserIds: [],
        bccUserIds: [],
        forwardVisibility: "PRIVATE",
        remarkText: null,
      });
      expect(payload).not.toHaveProperty("toUserId");
    });

    it("defaults omitted recipient lists to empty arrays and remark to null", () => {
      const payload = buildForwardPayload({ mode: "person", toUserId: "1", forwardVisibility: "PUBLIC" });
      expect(payload.ccUserIds).toEqual([]);
      expect(payload.bccUserIds).toEqual([]);
      expect(payload.remarkText).toBeNull();
    });
  });
});
