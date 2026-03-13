const TOKEN_KEY = "rms_access_token";
const USER_KEY = "rms_auth_user";

function normalizePermissions(permissions) {
  if (!Array.isArray(permissions)) return [];
  return [...new Set(permissions.map((x) => String(x || "").trim().toUpperCase()).filter(Boolean))];
}

export function getAccessToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function getCurrentUser() {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;

  try {
    const user = JSON.parse(raw);
    return user ? { ...user, permissions: normalizePermissions(user.permissions) } : null;
  } catch {
    return null;
  }
}

export function isAuthenticated() {
  return !!getAccessToken();
}

export function setSession(accessToken, user) {
  localStorage.setItem(TOKEN_KEY, accessToken);
  localStorage.setItem(USER_KEY, JSON.stringify({
    ...(user || {}),
    permissions: normalizePermissions(user?.permissions),
  }));
  window.dispatchEvent(new Event("rms_auth_changed"));
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  window.dispatchEvent(new Event("rms_auth_changed"));
}

export function hasPermission(userOrPermission, maybePermission) {
  const user = maybePermission ? userOrPermission : getCurrentUser();
  const permission = maybePermission || userOrPermission;
  if (!user || !permission) return false;
  return normalizePermissions(user.permissions).includes(String(permission).trim().toUpperCase());
}