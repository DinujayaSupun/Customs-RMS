import { expect, test } from "@playwright/test";
import { adminCreds, createTempUserByAdmin, loginFromUI } from "./helpers/auth";

test("admin can access users and permissions pages", async ({ page }) => {
  await loginFromUI(page, adminCreds);
  await expect(page).toHaveURL(/\/inbox$/);

  await expect(page.getByRole("link", { name: "Users" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Permissions" })).toBeVisible();

  await page.goto("/users");
  await expect(page.getByRole("heading", { name: "Users" })).toBeVisible();

  await page.goto("/permissions");
  await expect(page.getByRole("heading", { name: "Permissions" })).toBeVisible();
});

test("non-admin route guard redirects /users to /documents", async ({ page, request }) => {
  const nonAdminUser = await createTempUserByAdmin(request, "SC");
  await loginFromUI(page, nonAdminUser);
  await expect(page).toHaveURL(/\/inbox$/);
  await page.goto("/users");

  await expect(page).toHaveURL(/\/documents$/);
  await expect(page.getByRole("heading", { name: "Documents" })).toBeVisible();
});
