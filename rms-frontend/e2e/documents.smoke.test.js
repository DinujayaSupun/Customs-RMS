import { expect, test } from "@playwright/test";
import { assertUserHasPermissions, createTempUserByAdmin, loginFromUI } from "./helpers/auth";

test("non-admin user can open create page and create a document", async ({ page, request }) => {
  const user = await createTempUserByAdmin(request, "PMA");
  await assertUserHasPermissions(request, user, ["CREATE_DOCUMENT"], "document create UI test");

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
  await page.getByRole("button", { name: "Create" }).click();

  await expect(page).toHaveURL(/\/documents\/\d+$/);
});
