import { expect, test } from "@playwright/test";
import { assertUserHasPermissions, createDocumentByApi, createTempUserByAdmin, loginFromUI } from "./helpers/auth";

test("owner can upload and delete an attachment from document details", async ({ page, request }) => {
  const owner = await createTempUserByAdmin(request, "DC");
  await assertUserHasPermissions(
    request,
    owner,
    ["CREATE_DOCUMENT", "UPLOAD_ATTACHMENT", "DELETE_ATTACHMENT"],
    "attachment upload/delete test",
  );

  const document = await createDocumentByApi(request, owner);
  const fileName = `e2e-attachment-${Date.now()}.txt`;

  await loginFromUI(page, owner);
  await expect(page).toHaveURL(/\/inbox$/);

  await page.goto(`/documents/${document.id}`);
  await expect(page).toHaveURL(new RegExp(`/documents/${document.id}$`));
  await expect(page.getByText("No files yet.")).toBeVisible();

  await page.locator("#attachmentFileInput").setInputFiles({
    name: fileName,
    mimeType: "text/plain",
    buffer: Buffer.from("E2E attachment body"),
  });
  await expect(page.getByText(fileName)).toBeVisible();

  await page.getByRole("button", { name: "Upload Attachment" }).click();
  await expect(page.getByText("Attachment uploaded successfully.", { exact: true })).toBeVisible();

  const uploadedItem = page.locator(".item", { hasText: fileName }).first();
  await expect(uploadedItem).toBeVisible();
  await expect(uploadedItem).toContainText("v1");
  await expect(uploadedItem).toContainText("MAIN");

  page.once("dialog", async (dialog) => {
    expect(dialog.message()).toContain(fileName);
    await dialog.accept();
  });
  await uploadedItem.getByRole("button", { name: "Delete" }).click();

  await expect(page.getByText("Attachment deleted successfully.", { exact: true })).toBeVisible();
  await expect(page.locator(".item", { hasText: fileName })).toHaveCount(0);
  await expect(page.getByText("No files yet.")).toBeVisible();
});
