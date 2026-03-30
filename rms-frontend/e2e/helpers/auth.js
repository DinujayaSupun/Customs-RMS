import { expect } from "@playwright/test";

function requiredEnv(name) {
  const value = process.env[name];
  if (!value || !String(value).trim()) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

export const adminCreds = {
  username: requiredEnv("RMS_E2E_ADMIN_USER"),
  password: requiredEnv("RMS_E2E_ADMIN_PASS"),
};

export const dcCreds = {
  username: requiredEnv("RMS_E2E_DC_USER"),
  password: requiredEnv("RMS_E2E_DC_PASS"),
};

export const apiBaseUrl = process.env.RMS_E2E_API_BASE_URL || "http://localhost:8080/api";

export async function loginFromUI(page, { username, password }) {
  await page.goto("/login");
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
  await page.getByLabel("Username").fill(username);
  await page.getByLabel("Password").fill(password);
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
  const username = `e2e-${role.toLowerCase()}-${unique}`;
  const password = `E2e${unique}9`;

  const createResponse = await request.post(`${apiBaseUrl}/admin/users`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    data: {
      fullName: `E2E ${role} ${unique}`,
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
    refNo: overrides.refNo || `E2E-DOC-${unique}`,
    title: overrides.title || `E2E Document ${unique}`,
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

export async function forwardDocumentByApi(request, creds, documentId, toUserId, remarkText = "Forwarded by E2E") {
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
