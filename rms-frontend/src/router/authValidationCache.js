const AUTH_VALIDATION_TTL_MS = 2 * 60 * 1000;

let lastValidatedAt = 0;

export function shouldValidateAuth(now = Date.now()) {
  if (lastValidatedAt === 0) return true;
  return now - lastValidatedAt > AUTH_VALIDATION_TTL_MS;
}

export function markAuthValidated(now = Date.now()) {
  lastValidatedAt = now;
}

export function resetAuthValidation() {
  lastValidatedAt = 0;
}
