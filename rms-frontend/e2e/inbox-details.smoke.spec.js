import { expect, test } from "@playwright/test";
import {
  createDocumentByApi,
  createTempUserByAdmin,
  forwardDocumentByApi,
  loginFromUI,
} from "./helpers/auth";

test("forwarded document appears in recipient inbox and can be opened", async ({ page, request }) => {
  const dcUser = await createTempUserByAdmin(request, "DC");
  const ddcUser = await createTempUserByAdmin(request, "DDC");
  const document = await createDocumentByApi(request, dcUser);
  await forwardDocumentByApi(request, dcUser, document.id, ddcUser.id);

  await loginFromUI(page, ddcUser);
  await expect(page).toHaveURL(/\/inbox$/);

  await expect(page.getByText(document.refNo)).toBeVisible();
  await page.getByText(document.refNo).click();
  await expect(page).toHaveURL(new RegExp(`/documents/${document.id}$`));
});

test("owner can save minute in document details and see it in minutes list", async ({ page, request }) => {
  const owner = await createTempUserByAdmin(request, "DC");
  const document = await createDocumentByApi(request, owner);

  await loginFromUI(page, owner);
  await expect(page).toHaveURL(/\/inbox$/);
  await page.goto(`/documents/${document.id}`);
  await expect(page).toHaveURL(new RegExp(`/documents/${document.id}$`));
  await expect(page.getByPlaceholder("Type minute...")).toBeVisible();

  const remark = `E2E minute ${Date.now()}`;
  await page.getByPlaceholder("Type minute...").fill(remark);
  await page.getByRole("button", { name: "Save Minute" }).click();

  await expect(page.getByText(remark)).toBeVisible();
});

test("sender can switch to sent inbox and see forwarded document", async ({ page, request }) => {
  const dcUser = await createTempUserByAdmin(request, "DC");
  const ddcUser = await createTempUserByAdmin(request, "DDC");
  const document = await createDocumentByApi(request, dcUser);
  await forwardDocumentByApi(request, dcUser, document.id, ddcUser.id);

  await loginFromUI(page, dcUser);
  await expect(page).toHaveURL(/\/inbox$/);

  await page.getByRole("button", { name: "Sent" }).click();
  await expect(page.getByText(document.refNo)).toBeVisible();
});
