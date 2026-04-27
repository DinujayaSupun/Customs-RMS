import { expect } from "@playwright/test";

export const adminCreds = {
  username: process.env.RMS_E2E_ADMIN_USER || "admin",
  password: process.env.RMS_E2E_ADMIN_PASS || "E2eAdmin123",
};

export const dcCreds = {
  username: process.env.RMS_E2E_DC_USER || "dc",
  password: process.env.RMS_E2E_DC_PASS || "E2eDefault123",
};

export const apiBaseUrl = process.env.RMS_E2E_API_BASE_URL || "http://localhost:8081/api";
export const E2E_USER_PREFIX = "e2e-auto-";
export const E2E_DOC_PREFIX = "E2E-AUTO-";

export async function loginFromUI(page, { username, password }) {
  await page.goto("/login");
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
  await page.getByPlaceholder("Enter username").fill(username);
  await page.getByPlaceholder("Enter password").fill(password);
  await page.getByRole("button", { name: "Sign In" }).click();
}

export async function apiLogin(request, { username, password }) {
  const response = await request.post(`${apiBaseUrl}/auth/login`, {
    data: { username, password },
  });
  return {
    status: response.status(),
    body: await response.json(),
  };
}

export async function createTempUserByAdmin(request, role = "SC") {
  const loginResult = await apiLogin(request, adminCreds);
  if (loginResult.status !== 200) {
    throw new Error(`Admin login failed for E2E setup. Status: ${loginResult.status}`);
  }

  const accessToken = loginResult.body?.accessToken;
  if (!accessToken) {
    throw new Error("Admin login did not return an access token.");
  }

  const unique = `${Date.now()}-${Math.floor(Math.random() * 10_000)}`;
  const username = `${E2E_USER_PREFIX}${role.toLowerCase()}-${unique}`;
  const password = `E2e${unique}9`;

  const createResponse = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    data: {
      fullName: `E2E AUTO ${role} ${unique}`,
      username,
      email: `${username}@example.com`,
      phone: "0771234567",
      department: "E2E",
      role,
      password,
    },
  });

  if (createResponse.status() !== 200) {
    const body = await createResponse.text();
    throw new Error(`Failed to create temp ${role} user. Status: ${createResponse.status()} Body: ${body}`);
  }

  const created = await createResponse.json();
  return {
    id: created?.id,
    fullName: created?.fullName || `E2E AUTO ${role} ${unique}`,
    username,
    password,
    role,
  };
}

export async function createDocumentByApi(request, creds, overrides = {}) {
  const loginResult = await apiLogin(request, creds);
  if (loginResult.status !== 200) {
    throw new Error(`API login failed for document creation. Status: ${loginResult.status}`);
  }
  const accessToken = loginResult.body?.accessToken;

  const unique = `${Date.now()}-${Math.floor(Math.random() * 10_000)}`;
  const payload = {
    refNo: overrides.refNo || `${E2E_DOC_PREFIX}DOC-${unique}`,
    title: overrides.title || `${E2E_DOC_PREFIX}Document ${unique}`,
    receivedDate: overrides.receivedDate || "2026-03-25",
    companyName: overrides.companyName || "E2E Company",
    priority: overrides.priority || "HIGH",
  };

  const response = await request.post(`${apiBaseUrl}/documents`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    data: payload,
  });

  if (response.status() !== 201) {
    throw new Error(`Failed to create document. Status: ${response.status()} Body: ${await response.text()}`);
  }

  return await response.json();
}

export async function getDocumentByApi(request, creds, documentId) {
  const loginResult = await apiLogin(request, creds);
  if (loginResult.status !== 200) {
    throw new Error(`API login failed for document read. Status: ${loginResult.status}`);
  }
  const accessToken = loginResult.body?.accessToken;

  const response = await request.get(`${apiBaseUrl}/documents/${documentId}`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (response.status() !== 200) {
    throw new Error(`Failed to read document. Status: ${response.status()} Body: ${await response.text()}`);
  }

  return await response.json();
}

export async function getWorkflowConfigByAdmin(request) {
  const loginResult = await apiLogin(request, adminCreds);
  if (loginResult.status !== 200) {
    throw new Error(`Admin login failed for workflow config read. Status: ${loginResult.status}`);
  }
  const accessToken = loginResult.body?.accessToken;

  const response = await request.get(`${apiBaseUrl}/admin/permissions/dc-auto-forward`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (response.status() !== 200) {
    throw new Error(`Failed to read workflow config. Status: ${response.status()} Body: ${await response.text()}`);
  }

  return await response.json();
}

export async function updateWorkflowConfigByAdmin(request, config) {
  const loginResult = await apiLogin(request, adminCreds);
  if (loginResult.status !== 200) {
    throw new Error(`Admin login failed for workflow config update. Status: ${loginResult.status}`);
  }
  const accessToken = loginResult.body?.accessToken;

  const payload = {
    enabled: !!config?.enabled,
    timeoutMinutes: Number(config?.timeoutMinutes ?? 0),
    receiverUserId: config?.receiverUserId ?? null,
    forwardReturnAllowedStatuses: Array.isArray(config?.forwardReturnAllowedStatuses)
      ? config.forwardReturnAllowedStatuses
      : ["PENDING", "IN_PROGRESS", "RETURNED"],
    approveRejectButtonsEnabled: config?.approveRejectButtonsEnabled !== false,
  };

  const response = await request.put(`${apiBaseUrl}/admin/permissions/dc-auto-forward`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    data: payload,
  });

  if (response.status() !== 200) {
    throw new Error(`Failed to update workflow config. Status: ${response.status()} Body: ${await response.text()}`);
  }

  return await response.json();
}

export async function getPermissionMatrixByAdmin(request) {
  const loginResult = await apiLogin(request, adminCreds);
  if (loginResult.status !== 200) {
    throw new Error(`Admin login failed for permission matrix read. Status: ${loginResult.status}`);
  }
  const accessToken = loginResult.body?.accessToken;

  const response = await request.get(`${apiBaseUrl}/admin/permissions`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (response.status() !== 200) {
    throw new Error(`Failed to read permission matrix. Status: ${response.status()} Body: ${await response.text()}`);
  }

  return await response.json();
}

export async function updatePermissionMatrixByAdmin(request, entries) {
  const loginResult = await apiLogin(request, adminCreds);
  if (loginResult.status !== 200) {
    throw new Error(`Admin login failed for permission matrix update. Status: ${loginResult.status}`);
  }
  const accessToken = loginResult.body?.accessToken;

  const response = await request.put(`${apiBaseUrl}/admin/permissions`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    data: { entries },
  });

  if (response.status() !== 200) {
    throw new Error(`Failed to update permission matrix. Status: ${response.status()} Body: ${await response.text()}`);
  }

  return await response.json();
}

export async function forwardDocumentByApi(request, creds, documentId, toUserId, remarkText = "Forwarded by E2E AUTO") {
  const loginResult = await apiLogin(request, creds);
  if (loginResult.status !== 200) {
    throw new Error(`API login failed for document forward. Status: ${loginResult.status}`);
  }
  const accessToken = loginResult.body?.accessToken;

  const response = await request.post(`${apiBaseUrl}/documents/${documentId}/forward`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    data: {
      toUserId,
      forwardVisibility: "PRIVATE",
      remarkText,
    },
  });

  if (response.status() !== 200) {
    throw new Error(`Failed to forward document. Status: ${response.status()} Body: ${await response.text()}`);
  }
}
