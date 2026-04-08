import { expect, test } from "@playwright/test";
import { adminCreds, apiBaseUrl, apiLogin, createDocumentByApi, createTempUserByAdmin, loginFromUI } from "./helpers/auth";

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

test("permission update enables Approve button live for affected role", async ({ browser, request }) => {
  const user = await createTempUserByAdmin(request, "SC");
  const document = await createDocumentByApi(request, user);

  const userContext = await browser.newContext();
  const userPage = await userContext.newPage();

  let originalEntries = null;

  try {
    await loginFromUI(userPage, user);
    await expect(userPage).toHaveURL(/\/inbox$/);
    await userPage.goto(`/documents/${document.id}`);
    await expect(userPage).toHaveURL(new RegExp(`/documents/${document.id}$`));

    const approveBtn = userPage.getByRole("button", { name: "Approve" });
    await expect(approveBtn).toBeDisabled();

    const adminLogin = await apiLogin(request, adminCreds);
    expect(adminLogin.status).toBe(200);
    const adminToken = adminLogin.body?.accessToken;
    expect(adminToken).toBeTruthy();

    const matrixResp = await request.get(`${apiBaseUrl}/admin/permissions`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(matrixResp.status()).toBe(200);
    const matrixData = await matrixResp.json();

    originalEntries = Array.isArray(matrixData?.entries) ? matrixData.entries : [];
    const updatedEntries = originalEntries.map((entry) => {
      if (String(entry?.roleName || "").toUpperCase() === "SC" && String(entry?.permission || "").toUpperCase() === "APPROVE_DOCUMENT") {
        return { ...entry, enabled: true };
      }
      return entry;
    });

    const updateResp = await request.put(`${apiBaseUrl}/admin/permissions`, {
      headers: {
        Authorization: `Bearer ${adminToken}`,
        "Content-Type": "application/json",
      },
      data: { entries: updatedEntries },
    });
    expect(updateResp.status()).toBe(200);

    await expect(approveBtn).toBeEnabled({ timeout: 12000 });
  } finally {
    try {
      const adminLogin = await apiLogin(request, adminCreds);
      if (adminLogin.status === 200 && Array.isArray(originalEntries)) {
        await request.put(`${apiBaseUrl}/admin/permissions`, {
          headers: {
            Authorization: `Bearer ${adminLogin.body?.accessToken}`,
            "Content-Type": "application/json",
          },
          data: { entries: originalEntries },
        });
      }
    } catch {
      // Ignore restore failures in test cleanup path.
    }

    await userContext.close();
  }
});
