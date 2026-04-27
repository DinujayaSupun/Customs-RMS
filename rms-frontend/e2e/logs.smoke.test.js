import { expect, test } from "@playwright/test";
import { adminCreds, createTempUserByAdmin, loginFromUI } from "./helpers/auth";

test("admin can open logs page and view table content", async ({ page }) => {
  await loginFromUI(page, adminCreds);
  await expect(page).toHaveURL(/\/inbox$/);

  await page.goto("/logs");
  await expect(page.getByRole("heading", { name: "Audit Logs" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Apply" })).toBeVisible();
  await expect(page.locator("table")).toBeVisible();
});

test("user without VIEW_LOGS permission is redirected from /logs to /documents", async ({ page, request }) => {
  const nonAdmin = await createTempUserByAdmin(request, "SC");
  await loginFromUI(page, nonAdmin);
  await expect(page).toHaveURL(/\/inbox$/);

  await page.goto("/logs");
  await expect(page).toHaveURL(/\/documents$/);
  await expect(page.getByRole("heading", { name: "Documents" })).toBeVisible();
});
