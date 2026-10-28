import { useEffect, useRef, useState } from 'react';

/**
 * Turns an ever-growing counter into "per second" by comparing it with its previous value.
 *
 * `sample` should be something that changes on every poll (for example the whole stats object).
 * That way the rate drops back to 0 when the counter stops growing, instead of showing a stale number.
 */
export function useRate(counter: number | undefined, sample: unknown): number {
  const [rate, setRate] = useState(0);
  const previous = useRef<{ value: number; at: number } | null>(null);

  useEffect(() => {
    if (counter === undefined) {
      return;
    }
    const now = Date.now();
    const last = previous.current;
    if (last && counter >= last.value && now > last.at) {
      setRate((counter - last.value) / ((now - last.at) / 1000));
    } else if (last) {
      setRate(0); // the counter went backwards: the app restarted
    }
    previous.current = { value: counter, at: now };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sample]);

  return rate;
}
