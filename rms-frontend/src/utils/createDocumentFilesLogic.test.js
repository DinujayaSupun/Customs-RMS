import { describe, expect, it, vi } from "vitest";
import {
  addSelectedFiles,
  formatVersionedFileName,
  getSelectedFileRole,
  getSelectedFilesSummary,
  getSelectedFileVersionLabel,
  removeSelectedFile,
  uploadFilesInSelectedOrder,
} from "./createDocumentFilesLogic";

function file(name, size, lastModified = 1) {
  return { name, size, lastModified };
}

describe("createDocumentFilesLogic", () => {
  it("adds multiple selected files while preserving user selection order", () => {
    const main = file("main.pdf", 100);
    const appendix = file("appendix.txt", 50);

    const selected = addSelectedFiles([], [main, appendix]);

    expect(selected).toEqual([main, appendix]);
    expect(getSelectedFileRole(0)).toBe("Main file");
    expect(getSelectedFileRole(1)).toBe("Attachment");
    expect(getSelectedFileVersionLabel(0, main.name)).toBe("V1 - main.pdf");
    expect(getSelectedFileVersionLabel(1, appendix.name)).toBe("V2 - appendix.txt");
  });

  it("formats uploaded attachment names with the persisted version number", () => {
    expect(formatVersionedFileName(2, "customs file.docx")).toBe("V2 - customs file.docx");
    expect(formatVersionedFileName(null, "")).toBe("V1 - file");
  });

  it("summarizes selected files with future version labels", () => {
    const main = file("cusdec doc.pdf", 100);
    const customs = file("customs file.docx", 50);
    const invoice = file("invoice.pdf", 20);

    expect(getSelectedFilesSummary([])).toBe("No files chosen");
    expect(getSelectedFilesSummary([main])).toBe("1 file selected: V1 - cusdec doc.pdf");
    expect(getSelectedFilesSummary([main, customs, invoice])).toBe(
      "3 files selected: V1 - cusdec doc.pdf, V2 - customs file.docx + 1 more"
    );
  });

  it("appends newly selected files without duplicating the same file", () => {
    const main = file("main.pdf", 100, 10);
    const duplicateMain = file("main.pdf", 100, 10);
    const note = file("note.txt", 20, 20);

    const selected = addSelectedFiles([main], [duplicateMain, note]);

    expect(selected).toEqual([main, note]);
  });

  it("removes one selected file without changing the remaining order", () => {
    const main = file("main.pdf", 100);
    const wrong = file("wrong.pdf", 200);
    const appendix = file("appendix.txt", 50);

    const selected = removeSelectedFile([main, wrong, appendix], wrong);

    expect(selected).toEqual([main, appendix]);
  });

  it("uploads selected files in order so the first file becomes main/version 1", async () => {
    const main = file("main.pdf", 100);
    const appendix = file("appendix.txt", 50);
    const uploadAttachment = vi.fn().mockResolvedValue({});

    await uploadFilesInSelectedOrder(77, [main, appendix], uploadAttachment);

    expect(uploadAttachment).toHaveBeenNthCalledWith(1, 77, main);
    expect(uploadAttachment).toHaveBeenNthCalledWith(2, 77, appendix);
  });
});
