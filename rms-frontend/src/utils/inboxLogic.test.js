import { describe, expect, it } from "vitest";
import {
  buildInboxReceivedPreview,
  findPreferredReturnTargetId,
  markInboxDocumentViewed,
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

  it("shows actual sender first and latest minute author separately when they differ", () => {
    expect(
      buildInboxReceivedPreview(
        {
          inboxSenderUserId: 10,
          inboxSenderName: "Prasad",
          inboxSenderRole: "DC",
          latestRemarkByUserId: 20,
          latestRemarkByName: "Milinda",
          latestRemarkByRole: "ASC",
          latestRemarkTextPreview: "for pra",
          latestRemarkText: "for prasad full minute",
          latestRemarkAt: "2026-04-30T10:01:00",
        },
        () => "4:01 PM",
      ),
    ).toEqual({
      senderLine: "Sent by Prasad (DC)",
      minuteLine: "Last minute by Milinda (ASC) • 4:01 PM – for pra",
      minuteTooltip: "for prasad full minute",
      fallbackLine: null,
    });
  });

  it("does not repeat last minute by when sender and minute author are the same", () => {
    expect(
      buildInboxReceivedPreview(
        {
          inboxSenderUserId: 10,
          inboxSenderName: "Prasad",
          inboxSenderRole: "DC",
          latestRemarkByUserId: 10,
          latestRemarkByName: "Prasad",
          latestRemarkByRole: "DC",
          latestRemarkTextPreview: "approved to continue",
          latestRemarkText: "approved to continue with a longer explanation",
          latestRemarkAt: "2026-04-30T10:01:00",
        },
        () => "4:01 PM",
      ),
    ).toEqual({
      senderLine: "Sent by Prasad (DC) • 4:01 PM – approved to continue",
      minuteLine: null,
      minuteTooltip: "approved to continue with a longer explanation",
      fallbackLine: null,
    });
  });

  it("shows a no minute message under the actual sender when no minute is available", () => {
    expect(
      buildInboxReceivedPreview({
        inboxSenderUserId: 10,
        inboxSenderName: "Prasad",
        inboxSenderRole: "DC",
      }),
    ).toEqual({
      senderLine: "Sent by Prasad (DC)",
      minuteLine: null,
      minuteTooltip: null,
      fallbackLine: "No minute added",
    });
  });

  it("uses the preview as the tooltip fallback when full minute text is missing", () => {
    expect(
      buildInboxReceivedPreview(
        {
          inboxSenderUserId: 10,
          inboxSenderName: "Prasad",
          inboxSenderRole: "DC",
          latestRemarkByUserId: 20,
          latestRemarkByName: "Milinda",
          latestRemarkByRole: "ASC",
          latestRemarkTextPreview: "short minute",
          latestRemarkAt: "2026-04-30T10:01:00",
        },
        () => "4:01 PM",
      ).minuteTooltip,
    ).toBe("short minute");
  });

  it("shows received undo send by you from the pulled-back user", () => {
    expect(
      buildInboxReceivedPreview(
        {
          undoSendActionType: "UNDO_SEND",
          undoSendByUserId: 50,
          undoSendByName: "Pradeep",
          undoSendByRole: "PMA",
          undoSendFromUserId: 22,
          undoSendFromName: "Prasad",
          undoSendFromRole: "DC",
          latestRemarkByUserId: 50,
          latestRemarkByName: "Pradeep",
          latestRemarkByRole: "PMA",
          latestRemarkTextPreview: "mistaken forward",
          latestRemarkText: "mistaken forward full text",
          latestRemarkAt: "2026-05-04T09:30:00",
        },
        () => "9:30 AM",
        50,
      ),
    ).toEqual({
      senderLine: "Send undone by you from Prasad (DC)",
      minuteLine: "Last minute by Pradeep (PMA) • 9:30 AM – mistaken forward",
      minuteTooltip: "mistaken forward full text",
      fallbackLine: null,
    });
  });

  it("shows received undo send by another user from the pulled-back user", () => {
    expect(
      buildInboxReceivedPreview(
        {
          undoSendActionType: "UNDO_SEND",
          undoSendByUserId: 50,
          undoSendByName: "Pradeep",
          undoSendByRole: "PMA",
          undoSendFromUserId: 22,
          undoSendFromName: "Prasad",
          undoSendFromRole: "DC",
        },
        () => "9:30 AM",
        99,
      ).senderLine,
    ).toBe("Send undone by Pradeep (PMA) from Prasad (DC)");
  });

  it("marks the matching received document as viewed without changing other rows", () => {
    const rows = [
      { id: 101, viewedByMe: false, title: "First" },
      { documentId: 202, viewedByMe: false, title: "Second" },
      { id: 303, viewedByMe: false, title: "Third" },
    ];

    const updated = markInboxDocumentViewed(rows, 202);

    expect(updated).toEqual([
      { id: 101, viewedByMe: false, title: "First" },
      { documentId: 202, viewedByMe: true, title: "Second" },
      { id: 303, viewedByMe: false, title: "Third" },
    ]);
    expect(updated[0]).toBe(rows[0]);
    expect(updated[2]).toBe(rows[2]);
  });
});
