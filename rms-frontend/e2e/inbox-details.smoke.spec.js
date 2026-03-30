import { expect, test } from "@playwright/test";
import {
  apiBaseUrl,
  apiLogin,
  createDocumentByApi,
  createTempUserByAdmin,
  forwardDocumentByApi,
  loginFromUI,
} from "./helpers/auth";

async function waitForSentMessage(request, creds, refNo, timeoutMs = 10000) {
  const login = await apiLogin(request, creds);
  if (login.status !== 200 || !login.body?.accessToken) {
    throw new Error(`Failed to login for sent-message check. Status: ${login.status}`);
  }

  const token = login.body.accessToken;
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const response = await request.get(`${apiBaseUrl}/documents/sent-messages?page=0&size=300`, {
      headers: { Authorization: `Bearer ${token}` },
    });

    if (response.ok()) {
      const body = await response.json();
      const rows = Array.isArray(body) ? body : body?.content ?? body?.items ?? [];
      if (rows.some((row) => row?.refNo === refNo)) {
        return;
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 400));
  }

  throw new Error(`Timed out waiting for sent message ${refNo}`);
}

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
  await waitForSentMessage(request, dcUser, document.refNo, 12000);
  await page.getByRole("button", { name: "Refresh" }).click();
  await expect(page.getByText(document.refNo)).toBeVisible();
});
