import { useEffect, useRef, useState } from 'react';

const prefersReducedMotion = (): boolean =>
  typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

/**
 * Returns a number that glides from its previous value to `target`, so counters roll up instead of jumping.
 *
 * Concepts to look for: requestAnimationFrame for smooth animation, a ref that remembers the value shown
 * between renders, and the cleanup function that cancels a running animation when the target changes again.
 */
export function useAnimatedNumber(target: number, durationMs = 700): number {
  const [display, setDisplay] = useState(target);
  const shown = useRef(target);

  useEffect(() => {
    if (prefersReducedMotion() || shown.current === target) {
      shown.current = target;
      setDisplay(target);
      return;
    }
    const from = shown.current;
    const start = performance.now();
    let frame = 0;
    const step = (now: number) => {
      const t = Math.min(1, (now - start) / durationMs);
      const eased = 1 - Math.pow(1 - t, 3); // ease-out: fast at first, then settles
      shown.current = from + (target - from) * eased;
      setDisplay(shown.current);
      if (t < 1) {
        frame = requestAnimationFrame(step);
      }
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [target, durationMs]);

  return display;
}
