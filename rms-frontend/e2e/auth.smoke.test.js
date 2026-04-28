import { expect, test } from "@playwright/test";
import { adminCreds, loginFromUI } from "./helpers/auth";

test("login page renders and rejects invalid credentials", async ({ page }) => {
  await page.goto("/login");
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();

  await page.getByPlaceholder("Enter username").fill("invalid-user");
  await page.getByPlaceholder("Enter password").fill("invalid-pass");
  await page.getByRole("button", { name: "Sign In" }).click();

  await expect(page.getByText("Invalid username or password.")).toBeVisible();
});

test("session survives refresh and logout returns to login", async ({ page }) => {
  await loginFromUI(page, adminCreds);
  await expect(page).toHaveURL(/\/inbox$/);
  await expect(page.getByRole("link", { name: "Documents" })).toBeVisible();

  await page.reload();
  await expect(page.getByRole("link", { name: "Documents" })).toBeVisible();

  await page.getByRole("button", { name: "Logout" }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
});
