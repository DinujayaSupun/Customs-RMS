import { expect, test } from "@playwright/test";
import {
  adminCreds,
  assertUserHasPermissions,
  createDocumentByApi,
  createTempUserByAdmin,
  loginFromUI,
} from "./helpers/auth";

test("non-admin user can open create page and create a document", async ({ page, request }) => {
  const user = await createTempUserByAdmin(request, "PMA");
  await assertUserHasPermissions(request, user, ["CREATE_DOCUMENT", "UPLOAD_ATTACHMENT"], "document create UI test");

  await loginFromUI(page, user);
  await expect(page).toHaveURL(/\/inbox$/);

  await page.goto("/documents/new");
  await expect(page.getByRole("heading", { name: "Create Document" })).toBeVisible();

  const refNo = `E2E-AUTO-${Date.now()}`;
  await page.getByPlaceholder("DOC-001").fill(refNo);
  await page.locator('input[type="date"]').fill("2026-03-25");
  await page.getByPlaceholder("Document title").fill("E2E-AUTO Smoke Document");
  await page.getByPlaceholder("Company name").fill("E2E Company");
  await page.locator("select.input").first().selectOption("HIGH");

  const mainFileName = `e2e-create-main-${Date.now()}.txt`;
  const removedFileName = `e2e-create-removed-${Date.now()}.txt`;
  const attachmentFileName = `e2e-create-attachment-${Date.now()}.txt`;

  await page.locator("#createDocumentFileInput").setInputFiles([
    {
      name: mainFileName,
      mimeType: "text/plain",
      buffer: Buffer.from("Main document body"),
    },
    {
      name: removedFileName,
      mimeType: "text/plain",
      buffer: Buffer.from("Should be removed before submit"),
    },
    {
      name: attachmentFileName,
      mimeType: "text/plain",
      buffer: Buffer.from("Additional attachment body"),
    },
  ]);

  await expect(page.getByText(mainFileName)).toBeVisible();
  await expect(page.getByText(removedFileName)).toBeVisible();
  await expect(page.getByText(attachmentFileName)).toBeVisible();
  await expect(page.locator(".selectedFileRow", { hasText: mainFileName })).toContainText("Main file");
  await expect(page.locator(".selectedFileRow", { hasText: attachmentFileName })).toContainText("Attachment");

  await page.locator(".selectedFileRow", { hasText: removedFileName }).getByRole("button", { name: "Remove" }).click();
  await expect(page.getByText(removedFileName)).toHaveCount(0);

  await page.getByRole("button", { name: "Create" }).click();

  await expect(page).toHaveURL(/\/documents\/\d+$/);
  await expect(page.locator(".item", { hasText: mainFileName })).toContainText("v1");
  await expect(page.locator(".item", { hasText: mainFileName })).toContainText("MAIN");
  await expect(page.locator(".item", { hasText: attachmentFileName })).toContainText("v2");
  await expect(page.getByText(removedFileName)).toHaveCount(0);
});

test("admin-created document displays backend owner names even when admin is not in workflow user list", async ({ page, request }) => {
  const document = await createDocumentByApi(request, adminCreds, {
    title: `E2E-AUTO Admin Owner Display ${Date.now()}`,
    priority: "MEDIUM",
  });
  const ownerName = document.currentOwnerName || document.createdByName;
  expect(ownerName).toBeTruthy();

  await loginFromUI(page, adminCreds);
  await expect(page).toHaveURL(/\/inbox$/);

  await page.goto("/documents");
  const row = page.locator("tr", { hasText: document.refNo }).first();
  await expect(row).toBeVisible();
  await expect(row.locator(".ownerCell")).toContainText(ownerName);
  await expect(row.locator(".ownerCell")).not.toContainText("Unknown user");

  await page.goto(`/documents/${document.id}`);
  await expect(page).toHaveURL(new RegExp(`/documents/${document.id}$`));
  await expect(page.locator(".pill", { hasText: "Report At:" })).toContainText(ownerName);
  await expect(page.locator(".subMeta")).toContainText(ownerName);
  await expect(page.getByText("Unknown user")).toHaveCount(0);
});
