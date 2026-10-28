import { useEffect, useRef, useState } from 'react';
import { formatNumber } from '../format';
import { useAnimatedNumber } from '../hooks/useAnimatedNumber';

interface Props {
  label: string;
  value: number;
  unit: string;
  format?: (n: number) => string;
  /** 0 to 1. When given, a thin progress bar is drawn under the number. */
  progress?: number;
}

/** One number in the "Totals" row. The number rolls up smoothly and the tile flashes when it changes. */
export function StatTile({ label, value, unit, format = formatNumber, progress }: Props) {
  const shown = useAnimatedNumber(value);
  const [bump, setBump] = useState(false);
  const first = useRef(true);

  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    setBump(true);
    const timer = setTimeout(() => setBump(false), 450);
    return () => clearTimeout(timer);
  }, [value]);

  return (
    <div className={`tile${bump ? ' bump' : ''}`}>
      <div className="k">{label}</div>
      <div className="v">{format(Math.round(shown))}</div>
      <div className="u">{unit}</div>
      {progress !== undefined && (
        <div className="bar thin" aria-hidden="true">
          <i style={{ width: `${Math.round(Math.min(1, Math.max(0, progress)) * 100)}%` }} />
        </div>
      )}
    </div>
  );
}
