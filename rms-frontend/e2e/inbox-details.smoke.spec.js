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

test("report-at user can save minute in document details and see it in minutes list", async ({ page, request }) => {
  const reportAtUser = await createTempUserByAdmin(request, "DC");
  const document = await createDocumentByApi(request, reportAtUser);

  await loginFromUI(page, reportAtUser);
  await expect(page).toHaveURL(/\/inbox$/);
  await page.goto(`/documents/${document.id}`);
  await expect(page).toHaveURL(new RegExp(`/documents/${document.id}$`));
  await expect(page.getByPlaceholder("Type minute...")).toBeVisible();

  const minute = `E2E minute ${Date.now()}`;
  await page.getByPlaceholder("Type minute...").fill(minute);
  await page.getByRole("button", { name: "Save Minute" }).click();

  await expect(page.getByText(minute)).toBeVisible();
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

test("browser notification click opens the exact forwarded document", async ({ page, request }) => {
  const sender = await createTempUserByAdmin(request, "DC");
  const recipient = await createTempUserByAdmin(request, "DDC");
  const document = await createDocumentByApi(request, sender);

  await page.addInitScript(() => {
    class MockNotification {
      static permission = "granted";

      static requestPermission() {
        return Promise.resolve("granted");
      }

      constructor(title, options) {
        this.title = title;
        this.options = options || {};
        this.onclick = null;

        window.dispatchEvent(new CustomEvent("rms_mock_notification_created", {
          detail: { title, body: this.options.body || "" },
        }));

        setTimeout(() => {
          if (typeof this.onclick === "function") {
            this.onclick();
          }
        }, 50);
      }

      close() {}
    }

    window.Notification = MockNotification;
    window.__rmsNotificationTrace = [];
    window.addEventListener("rms_notification_trace", (event) => {
      window.__rmsNotificationTrace.push(event.detail);
    });
  });

  await loginFromUI(page, recipient);
  await expect(page).toHaveURL(/\/inbox$/);

  await forwardDocumentByApi(request, sender, document.id, recipient.id, "Forward for notification click test");

  await expect(page).toHaveURL(new RegExp(`/documents/${document.id}$`));

  const traces = await page.evaluate(() => window.__rmsNotificationTrace || []);
  const stages = traces.map((t) => t?.stage);
  expect(stages).toContain("shown");
  expect(stages).toContain("clicked");
  expect(stages).toContain("navigated");

  const targetMatch = traces.some(
    (t) => t?.targetPath === `/documents/${document.id}` && Number(t?.documentId) === Number(document.id),
  );
  expect(targetMatch).toBeTruthy();
});
