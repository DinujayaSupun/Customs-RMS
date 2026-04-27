import { describe, expect, it } from "vitest";
import {
  findPreferredReturnTargetId,
  resolveWorkflowAutoTarget,
  sortInboxDefaultDisplay,
} from "./inboxLogic";

describe("inboxLogic", () => {
  it("puts urgent messages above newer non-urgent ones in the default display", () => {
    const rows = [
      { id: 1, priority: "HIGH", inboxReceivedAt: "2026-04-27T09:30:00Z" },
      { id: 2, priority: "URGENT", inboxReceivedAt: "2026-04-27T08:00:00Z" },
      { id: 3, priority: "HIGH", inboxReceivedAt: "2026-04-27T10:30:00Z" },
    ];

    const sorted = sortInboxDefaultDisplay(rows);
    expect(sorted.map((row) => row.id)).toEqual([2, 3, 1]);
  });

  it("suggests the most recent sender to the current user as the return target", () => {
    const targetId = findPreferredReturnTargetId({
      canReturn: true,
      currentUserId: 77,
      forwardTargets: [{ id: 11 }, { id: 22 }, { id: 33 }],
      forwardMovements: [
        { actionType: "FORWARD", fromUserId: 11, toUserId: 77 },
        { actionType: "FORWARD", fromUserId: 22, toUserId: 99 },
        { actionType: "RETURN", fromUserId: 33, toUserId: 77 },
      ],
    });

    expect(targetId).toBe(33);
  });

  it("keeps a valid manually selected target instead of overwriting it", () => {
    const resolved = resolveWorkflowAutoTarget({
      candidateTargets: [{ id: 11 }, { id: 22 }],
      preferredReturnTargetId: 11,
      currentTargetId: 22,
      autoSelectedTargetId: null,
    });

    expect(resolved).toEqual({
      targetId: 22,
      autoSelectedTargetId: null,
    });
  });
});
