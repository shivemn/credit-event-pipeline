import { useEffect, useRef, useState } from 'react';

/**
 * Calls `fetcher` straight away and then every `intervalMs`, and returns the latest result.
 *
 * Concepts to look for here:
 *  - useState holds data that, when it changes, re-renders the component.
 *  - useEffect runs side effects (here: a timer) and returns a cleanup function.
 *  - useRef keeps the newest `fetcher` without restarting the timer each time the parent re-renders.
 *  - `resetKey` restarts polling when it changes, for example when a different borrower is selected.
 *  - `enabled` pauses polling, for example while the live stream is delivering the same data.
 */
export function usePolling<T>(fetcher: () => Promise<T>, intervalMs: number, resetKey: unknown = null, enabled = true) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const latestFetcher = useRef(fetcher);
  latestFetcher.current = fetcher;

  useEffect(() => {
    if (!enabled) {
      return;
    }
    let cancelled = false;
    setData(null);

    const tick = async () => {
      try {
        const result = await latestFetcher.current();
        if (!cancelled) {
          setData(result);
          setError(null);
        }
      } catch (e) {
        if (!cancelled) {
          setError(e as Error);
        }
      }
    };

    tick();
    const timer = setInterval(tick, intervalMs);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [intervalMs, resetKey, enabled]);

  return { data, error };
}
