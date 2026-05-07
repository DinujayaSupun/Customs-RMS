import { expect, test } from "@playwright/test";
import {
  assertUserHasPermissions,
  createDocumentByApi,
  createTempUserByAdmin,
  forwardDocumentByApi,
  getDocumentByApi,
  getPermissionMatrixByAdmin,
  getWorkflowConfigByAdmin,
  loginFromUI,
  updatePermissionMatrixByAdmin,
  updateWorkflowConfigByAdmin,
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
  await assertUserHasPermissions(request, reportAtUser, ["CREATE_DOCUMENT", "ADD_REMARK"], "minute save test");

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
  await assertUserHasPermissions(
    request,
    dcUser,
    ["CREATE_DOCUMENT", "FORWARD_DOCUMENT", "FORWARD_PRIVATE", "VIEW_SENT_MESSAGES"],
    "sent inbox test",
  );

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

test("recipient can return a forwarded document from inbox popup to the latest sender", async ({ page, request }) => {
  const sender = await createTempUserByAdmin(request, "DC");
  const recipient = await createTempUserByAdmin(request, "DDC");
  await assertUserHasPermissions(request, recipient, ["RETURN_DOCUMENT"], "inbox return test");

  const document = await createDocumentByApi(request, sender);
  await forwardDocumentByApi(request, sender, document.id, recipient.id, "Forward for inbox return test");

  await loginFromUI(page, recipient);
  await expect(page).toHaveURL(/\/inbox$/);

  const row = page.locator(".mailRow", { hasText: document.refNo }).first();
  await expect(row).toBeVisible();

  await row.getByRole("button", { name: "Forward document" }).click();
  await expect(page.getByText("Forward Document")).toBeVisible();
  await expect(page.locator(".forwardSelected").first()).toContainText(sender.fullName);
  await expect(page.locator('input[placeholder*="Search user"]')).toHaveValue("");

  await page.getByRole("button", { name: "Return" }).click();
  await expect(page.getByText("Forward Document")).not.toBeVisible();

  await expect.poll(async () => {
    const refreshed = await getDocumentByApi(request, sender, document.id);
    return Number(refreshed.currentOwnerUserId);
  }).toBe(Number(sender.id));

  await expect(page.getByText(document.refNo)).not.toBeVisible();
});

test("owner can approve then mark done and sees Closed in details and documents list", async ({ page, request }) => {
  const originalPermissionMatrix = await getPermissionMatrixByAdmin(request);
  const originalWorkflowConfig = await getWorkflowConfigByAdmin(request);
  await updateWorkflowConfigByAdmin(request, {
    ...originalWorkflowConfig,
    approveRejectButtonsEnabled: true,
    forwardReturnAllowedStatuses: Array.isArray(originalWorkflowConfig?.forwardReturnAllowedStatuses)
      ? originalWorkflowConfig.forwardReturnAllowedStatuses
      : ["PENDING", "IN_PROGRESS", "RETURNED"],
  });

  const creator = await createTempUserByAdmin(request, "DC");
  const approver = await createTempUserByAdmin(request, "DDC");
  await assertUserHasPermissions(
    request,
    creator,
    ["CREATE_DOCUMENT", "FORWARD_DOCUMENT", "FORWARD_PRIVATE"],
    "approve and done setup",
  );

  const originalEntries = Array.isArray(originalPermissionMatrix?.entries) ? originalPermissionMatrix.entries : [];
  const updatedEntries = originalEntries.map((entry) => {
    if (String(entry?.roleName || "").toUpperCase() !== "DDC") return entry;

    const permission = String(entry?.permission || "").toUpperCase();
    if (permission === "APPROVE_DOCUMENT" || permission === "ISSUE_DOCUMENT") {
      return { ...entry, enabled: true };
    }
    return entry;
  });
  await updatePermissionMatrixByAdmin(request, updatedEntries);

  const document = await createDocumentByApi(request, creator);
  await forwardDocumentByApi(request, creator, document.id, approver.id, "Forward for approve and done test");

  try {
    await loginFromUI(page, approver);
    await expect(page).toHaveURL(/\/inbox$/);

    await page.getByText(document.refNo).click();
    await expect(page).toHaveURL(new RegExp(`/documents/${document.id}$`));

    await expect(page.getByRole("button", { name: "Approve" })).toBeEnabled();
    await page.getByRole("button", { name: "Approve" }).click();
    await expect.poll(async () => {
      const refreshed = await getDocumentByApi(request, approver, document.id);
      return refreshed.status;
    }).toBe("APPROVED");

    await expect(page.getByRole("button", { name: "Done" })).toBeEnabled();
    await page.getByRole("button", { name: "Done" }).click();
    await expect.poll(async () => {
      const refreshed = await getDocumentByApi(request, approver, document.id);
      return refreshed.status;
    }).toBe("ISSUED");

    await expect(page.getByText("Closed")).toBeVisible();

    await page.goto("/documents");
    const documentsRow = page.locator("tr", { hasText: document.refNo }).first();
    await expect(documentsRow).toBeVisible();
    await expect(documentsRow).toContainText("Closed");
  } finally {
    await updatePermissionMatrixByAdmin(request, originalEntries);
    await updateWorkflowConfigByAdmin(request, originalWorkflowConfig);
  }
});

test("owner can mark done directly when approve and reject buttons are disabled", async ({ page, request }) => {
  const originalWorkflowConfig = await getWorkflowConfigByAdmin(request);
  const owner = await createTempUserByAdmin(request, "DC");
  await assertUserHasPermissions(
    request,
    owner,
    ["CREATE_DOCUMENT", "ISSUE_DOCUMENT"],
    "direct done workflow test",
  );

  const document = await createDocumentByApi(request, owner);

  try {
    await updateWorkflowConfigByAdmin(request, {
      ...originalWorkflowConfig,
      approveRejectButtonsEnabled: false,
      forwardReturnAllowedStatuses: Array.isArray(originalWorkflowConfig?.forwardReturnAllowedStatuses)
        ? originalWorkflowConfig.forwardReturnAllowedStatuses
        : ["PENDING", "IN_PROGRESS", "RETURNED"],
    });

    await loginFromUI(page, owner);
    await expect(page).toHaveURL(/\/inbox$/);

    await page.goto(`/documents/${document.id}`);
    await expect(page).toHaveURL(new RegExp(`/documents/${document.id}$`));

    await expect(page.getByRole("button", { name: "Approve" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Reject" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Done" })).toBeEnabled();

    await page.getByRole("button", { name: "Done" }).click();
    await expect.poll(async () => {
      const refreshed = await getDocumentByApi(request, owner, document.id);
      return refreshed.status;
    }).toBe("ISSUED");

    await expect(page.getByText("Closed")).toBeVisible();
  } finally {
    await updateWorkflowConfigByAdmin(request, originalWorkflowConfig);
  }
});
