import { beforeEach, describe, expect, it, vi } from "vitest";
import { getBackendStatus, setBackendStatus } from "./backendStatus";

describe("backend status", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    setBackendStatus("unknown");
  });

  it("dispatches status changes only when the value changes", () => {
    const dispatchSpy = vi.spyOn(window, "dispatchEvent");

    setBackendStatus("available");
    setBackendStatus("available");
    setBackendStatus("unavailable");

    expect(getBackendStatus()).toBe("unavailable");
    expect(dispatchSpy).toHaveBeenCalledTimes(2);
  });
});
