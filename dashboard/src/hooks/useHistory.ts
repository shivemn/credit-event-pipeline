import { useEffect, useRef, useState } from 'react';

/**
 * Remembers the last `max` values of `value`, sampled once per second.
 * A timer with a cleanup function, plus a ref that always holds the newest value.
 */
export function useHistory(value: number, max = 60): number[] {
  const [history, setHistory] = useState<number[]>([]);
  const latest = useRef(value);
  latest.current = value;

  useEffect(() => {
    const timer = setInterval(() => {
      setHistory((previous) => [...previous, latest.current].slice(-max));
    }, 1000);
    return () => clearInterval(timer);
  }, [max]);

  return history;
}
