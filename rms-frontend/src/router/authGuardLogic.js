export function shouldClearSessionForAuthCheckError(error) {
  return !!error?.isAuthError || error?.status === 401 || error?.status === 403;
}

// Only allow same-site absolute paths as a post-login redirect target. Rejects full URLs
// ("https://evil.com"), protocol-relative ("//evil.com") and backslash tricks ("/\\evil.com")
// so a crafted ?redirect= cannot bounce the user off-site after authenticating.
export function safeLoginRedirect(raw, fallback = "/inbox") {
  const value = raw == null ? "" : String(raw);
  if (value.startsWith("/") && !value.startsWith("//") && !value.startsWith("/\\")) {
    return value;
  }
  return fallback;
}
