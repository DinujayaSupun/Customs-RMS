import { describe, expect, it } from "vitest";
import {
  getAttachmentViewerState,
  getPrimaryAttachment,
  isPreviewableAttachmentName,
  resolveAttachmentTypeFromName,
  sortAttachmentsByVersion,
} from "./attachmentViewerLogic";

describe("attachmentViewerLogic", () => {
  it("sorts attachments by version and keeps version 1 as the primary file", () => {
    const attachments = [
      { id: 22, versionNo: 3, fileName: "v3.pdf" },
      { id: 11, versionNo: 1, fileName: "v1.pdf" },
      { id: 18, versionNo: 2, fileName: "v2.pdf" },
    ];

    expect(sortAttachmentsByVersion(attachments).map((item) => item.id)).toEqual([11, 18, 22]);
    expect(getPrimaryAttachment(attachments)?.id).toBe(11);
  });

  it("falls back to the earliest available version when version 1 is missing", () => {
    const attachments = [
      { id: 40, versionNo: 5, fileName: "v5.pdf" },
      { id: 30, versionNo: 2, fileName: "v2.pdf" },
    ];

    expect(getPrimaryAttachment(attachments)?.id).toBe(30);
  });

  it("falls back to the primary file when the selected attachment is missing", () => {
    const state = getAttachmentViewerState(
      [
        { id: 7, versionNo: 1, fileName: "main.pdf" },
        { id: 8, versionNo: 2, fileName: "next.pdf" },
      ],
      999,
    );

    expect(state.primaryAttachment?.id).toBe(7);
    expect(state.selectedAttachment?.id).toBe(7);
    expect(state.selectedIndex).toBe(0);
    expect(state.canGoPrevious).toBe(false);
    expect(state.canGoNext).toBe(true);
  });

  it("tracks selection position for attachment navigation controls", () => {
    const state = getAttachmentViewerState(
      [
        { id: 7, versionNo: 1, fileName: "main.pdf" },
        { id: 8, versionNo: 2, fileName: "next.pdf" },
        { id: 9, versionNo: 3, fileName: "last.pdf" },
      ],
      8,
    );

    expect(state.selectedAttachment?.id).toBe(8);
    expect(state.selectedIndex).toBe(1);
    expect(state.canGoPrevious).toBe(true);
    expect(state.canGoNext).toBe(true);
  });

  it("marks only browser-previewable file names as previewable", () => {
    expect(isPreviewableAttachmentName("letter.pdf")).toBe(true);
    expect(isPreviewableAttachmentName("photo.JPG")).toBe(true);
    expect(isPreviewableAttachmentName("scan.webp")).toBe(true);
    expect(isPreviewableAttachmentName("sheet.xlsx")).toBe(false);
    expect(isPreviewableAttachmentName("archive.zip")).toBe(false);
    expect(isPreviewableAttachmentName("")).toBe(false);
  });

  it("resolves common attachment file types from file names", () => {
    expect(resolveAttachmentTypeFromName("letter.pdf")).toBe("PDF");
    expect(resolveAttachmentTypeFromName("letter.docx")).toBe("DOC");
    expect(resolveAttachmentTypeFromName("sheet.csv")).toBe("XLS");
    expect(resolveAttachmentTypeFromName("scan.bmp")).toBe("IMG");
    expect(resolveAttachmentTypeFromName("notes.txt")).toBe("TXT");
    expect(resolveAttachmentTypeFromName("bundle.7z")).toBe("ZIP");
    expect(resolveAttachmentTypeFromName("unknown.bin")).toBe("FILE");
  });
});
