import { describe, expect, it, vi } from "vitest";
import { effectScope, nextTick, ref } from "vue";
import { useDebouncedWatch } from "./useDebouncedWatch";

describe("useDebouncedWatch", () => {
  it("runs once after rapid changes settle", async () => {
    vi.useFakeTimers();
    const calls = [];
    const scope = effectScope();
    const search = ref("");

    scope.run(() => {
      useDebouncedWatch(search, (value) => calls.push(value), 300);
    });

    search.value = "a";
    await nextTick();
    search.value = "ab";
    await nextTick();
    search.value = "abc";
    await nextTick();

    vi.advanceTimersByTime(299);
    expect(calls).toEqual([]);

    vi.advanceTimersByTime(1);
    expect(calls).toEqual(["abc"]);

    scope.stop();
    vi.useRealTimers();
  });

  it("clears pending timers when the scope stops", async () => {
    vi.useFakeTimers();
    const callback = vi.fn();
    const scope = effectScope();
    const search = ref("");

    scope.run(() => {
      useDebouncedWatch(search, callback, 300);
    });

    search.value = "customs";
    await nextTick();
    scope.stop();
    vi.advanceTimersByTime(300);

    expect(callback).not.toHaveBeenCalled();
    vi.useRealTimers();
  });
});
