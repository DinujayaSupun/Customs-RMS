import { expect, test } from "@playwright/test";
import { createTempUserByAdmin, loginFromUI } from "./helpers/auth";

test("user can update profile fields and changes persist after reload", async ({ page, request }) => {
  const user = await createTempUserByAdmin(request, "SC");
  await loginFromUI(page, user);
  await expect(page).toHaveURL(/\/inbox$/);

  await page.goto("/profile");
  await expect(page.getByRole("heading", { name: "My Profile" })).toBeVisible();

  const fullNameRow = page.locator(".formRow").filter({ hasText: "Full Name" });
  const emailRow = page.locator(".formRow").filter({ hasText: "Email" });
  const phoneRow = page.locator(".formRow").filter({ hasText: "Phone" });

  const newName = `E2E Updated ${Date.now()}`;
  await fullNameRow.locator("input").fill(newName);
  await emailRow.locator("input").fill("e2e-updated@example.com");
  await phoneRow.locator("input").fill("0771234567");
  await page.getByRole("button", { name: "Save Profile" }).click();

  await expect(fullNameRow.locator("input")).toHaveValue(newName);
  await page.reload();
  await expect(fullNameRow.locator("input")).toHaveValue(newName);
});

test("user can change password and login with the new password", async ({ page, request }) => {
  const user = await createTempUserByAdmin(request, "SC");
  await loginFromUI(page, user);
  await expect(page).toHaveURL(/\/inbox$/);

  const newPassword = `New${Date.now()}9`;
  await page.goto("/profile");

  const passwordInputs = page.locator('input[type="password"]');
  await passwordInputs.nth(0).fill(user.password);
  await passwordInputs.nth(1).fill(newPassword);
  await passwordInputs.nth(2).fill(newPassword);
  await Promise.all([
    page.waitForResponse((response) =>
      response.url().includes("/api/auth/me/password") && response.status() === 204
    ),
    page.getByRole("button", { name: "Update Password" }).click(),
  ]);
  await expect(page.getByText("Password changed successfully.")).toBeVisible();

  await page.getByRole("button", { name: "Logout" }).click();
  await expect(page).toHaveURL(/\/login$/);

  await loginFromUI(page, { username: user.username, password: newPassword });
  await expect(page).toHaveURL(/\/inbox$/);
});
