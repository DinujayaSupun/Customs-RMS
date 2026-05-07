import { describe, expect, it } from "vitest";
import { getUndoSendInfo, needsUndoReason } from "./undoSendLogic";

describe("undoSendLogic", () => {
  it("shows an undo button when backend says undo is available", () => {
    const info = getUndoSendInfo({ canUndoSend: true, undoSendStatus: "AVAILABLE" });

    expect(info.canUndo).toBe(true);
    expect(info.label).toBe("Undo Send");
  });

  it("shows expired info when admin setting allows unavailable details", () => {
    const info = getUndoSendInfo({
      canUndoSend: false,
      undoSendStatus: "EXPIRED",
      undoSendShowExpiredInfo: true,
    });

    expect(info.canUndo).toBe(false);
    expect(info.helper).toBe("Undo Send expired");
  });

  it("does not show an undo notice on the original sender sent row", () => {
    const info = getUndoSendInfo({
      canUndoSend: false,
      undoSendStatus: "ALREADY_MOVED",
      undoSendActionType: "FORWARD",
      undoSendByUserId: 22,
      undoSendByName: "Prasad",
      undoSendByRole: "DC",
    });

    expect(info.canUndo).toBe(false);
    expect(info.isUndoNotice).toBe(false);
    expect(info.isReceiverUndoNotice).toBe(false);
    expect(info.helper).toBe("");
  });

  it("identifies automatic receiver sent-box undo notices", () => {
    const info = getUndoSendInfo({
      undoSendActionType: "UNDO_SEND",
      undoSendByName: "Prasad",
      undoSendByRole: "DC",
    });

    expect(info.isUndoNotice).toBe(true);
    expect(info.isReceiverUndoNotice).toBe(true);
    expect(info.helper).toBe("Undone by Prasad (DC)");
  });

  it("hides unavailable info when admin setting disables expired details", () => {
    const info = getUndoSendInfo({
      canUndoSend: false,
      undoSendStatus: "OPENED",
      undoSendShowExpiredInfo: false,
    });

    expect(info.helper).toBe("");
  });

  it("requires a reason unless backend explicitly disables it", () => {
    expect(needsUndoReason({ undoSendRequiresReason: true })).toBe(true);
    expect(needsUndoReason({ undoSendRequiresReason: false })).toBe(false);
  });
});
