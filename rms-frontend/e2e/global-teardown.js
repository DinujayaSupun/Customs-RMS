const apiBaseUrl = process.env.RMS_E2E_API_BASE_URL || "http://localhost:8081/api";
const E2E_USER_PREFIX = "e2e-auto-";
const E2E_DOC_PREFIX = "E2E-AUTO-";

function envValue(name) {
  return process.env[name] || "";
}

const adminCreds = {
  username: envValue("RMS_E2E_ADMIN_USER") || "admin",
  password: envValue("RMS_E2E_ADMIN_PASS") || "E2eAdmin123",
};

const dcCreds = {
  username: envValue("RMS_E2E_DC_USER") || "dc",
  password: envValue("RMS_E2E_DC_PASS") || "E2eDefault123",
};

async function apiLogin(username, password) {
  const response = await fetch(`${apiBaseUrl}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });

  if (!response.ok) {
    return null;
  }

  return response.json();
}

async function listAllPages(path, token, extraParams = {}) {
  const size = 200;
  const maxPages = 50;
  const all = [];

  for (let page = 0; page < maxPages; page += 1) {
    const url = new URL(`${apiBaseUrl}${path}`);
    url.searchParams.set("page", String(page));
    url.searchParams.set("size", String(size));

    Object.entries(extraParams).forEach(([k, v]) => {
      if (v !== undefined && v !== null && String(v).length > 0) {
        url.searchParams.set(k, String(v));
      }
    });

    const response = await fetch(url.toString(), {
      headers: { Authorization: `Bearer ${token}` },
    });

    if (!response.ok) {
      break;
    }

    const data = await response.json();
    const list = Array.isArray(data) ? data : (data?.content || data?.items || []);
    all.push(...list);

    if (Array.isArray(data) || list.length < size || page >= ((data?.totalPages || 1) - 1)) {
      break;
    }
  }

  return all;
}

async function deleteE2eDocuments(adminToken) {
  const docs = await listAllPages("/documents", adminToken, { search: E2E_DOC_PREFIX });

  const e2eDocs = docs.filter((d) => {
    const refNo = String(d?.refNo || "");
    const title = String(d?.title || "");
    return refNo.startsWith(E2E_DOC_PREFIX) || title.startsWith(E2E_DOC_PREFIX);
  });

  for (const doc of e2eDocs) {
    try {
      await fetch(`${apiBaseUrl}/documents/${doc.id}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${adminToken}` },
      });
    } catch {
      // Ignore delete failures to avoid breaking teardown.
    }
  }

  return e2eDocs.length;
}

async function findFallbackDcUserId(adminToken) {
  const dcLogin = await apiLogin(dcCreds.username, dcCreds.password);
  if (dcLogin?.userId) return dcLogin.userId;

  const users = await listAllPages("/admin/users", adminToken, { role: "DC", active: true });
  const firstActiveDc = users.find((u) => String(u?.role || "").toUpperCase() === "DC" && !!u?.active);
  return firstActiveDc?.id || null;
}

async function deactivateE2eUsers(adminToken, fallbackDcUserId) {
  if (!fallbackDcUserId) return 0;

  const users = await listAllPages("/admin/users", adminToken, { search: E2E_USER_PREFIX });
  const e2eUsers = users.filter((u) => String(u?.username || "").toLowerCase().startsWith(E2E_USER_PREFIX));

  // Deactivate active users only; non-DC first, then DC.
  const activeUsers = e2eUsers
    .filter((u) => !!u?.active)
    .sort((a, b) => {
      const aDc = String(a?.role || "").toUpperCase() === "DC" ? 1 : 0;
      const bDc = String(b?.role || "").toUpperCase() === "DC" ? 1 : 0;
      return aDc - bDc;
    });

  for (const user of activeUsers) {
    try {
      await fetch(`${apiBaseUrl}/admin/users/${user.id}/deactivate`, {
        method: "PATCH",
        headers: {
          Authorization: `Bearer ${adminToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ fallbackDcUserId }),
      });
    } catch {
      // Ignore deactivate failures to avoid breaking teardown.
    }
  }

  return activeUsers.length;
}

export default async function globalTeardown() {
  try {
    if (!adminCreds.username || !adminCreds.password) {
      console.warn("[e2e-cleanup] E2E admin credentials missing; skipping cleanup.");
      return;
    }

    const adminLogin = await apiLogin(adminCreds.username, adminCreds.password);
    const adminToken = adminLogin?.accessToken;

    if (!adminToken) {
      console.warn("[e2e-cleanup] Admin login failed; skipping cleanup.");
      return;
    }

    const deletedDocs = await deleteE2eDocuments(adminToken);
    const fallbackDcUserId = await findFallbackDcUserId(adminToken);
    const deactivatedUsers = await deactivateE2eUsers(adminToken, fallbackDcUserId);

    console.log(`[e2e-cleanup] Deleted docs: ${deletedDocs}, deactivated users: ${deactivatedUsers}`);
  } catch (error) {
    console.warn("[e2e-cleanup] Cleanup failed:", error?.message || error);
  }
}
