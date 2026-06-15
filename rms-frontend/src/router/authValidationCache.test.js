import { beforeEach, describe, expect, it } from "vitest";
import { markAuthValidated, resetAuthValidation, shouldValidateAuth } from "./authValidationCache";

describe("auth validation cache", () => {
  beforeEach(() => {
    resetAuthValidation();
  });

  it("skips repeated auth validation inside the ttl", () => {
    expect(shouldValidateAuth(1_000)).toBe(true);
    markAuthValidated(1_000);
    expect(shouldValidateAuth(30_000)).toBe(false);
    expect(shouldValidateAuth(130_001)).toBe(true);
  });
});
