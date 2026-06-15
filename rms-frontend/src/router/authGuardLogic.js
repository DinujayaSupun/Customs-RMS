export function shouldClearSessionForAuthCheckError(error) {
  return !!error?.isAuthError || error?.status === 401 || error?.status === 403;
}
