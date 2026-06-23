import { onScopeDispose, watch } from "vue";

export function useDebouncedWatch(source, callback, delayMs = 300, options = {}) {
  let timerId = null;

  const clearTimer = () => {
    if (timerId !== null) {
      window.clearTimeout(timerId);
      timerId = null;
    }
  };

  const stop = watch(
    source,
    (value, oldValue, onCleanup) => {
      clearTimer();
      timerId = window.setTimeout(() => {
        timerId = null;
        callback(value, oldValue);
      }, delayMs);
      onCleanup(clearTimer);
    },
    options,
  );

  onScopeDispose(() => {
    clearTimer();
    stop();
  });

  return () => {
    clearTimer();
    stop();
  };
}
