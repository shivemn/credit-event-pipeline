import { useState } from 'react';
import type { PointerEvent } from 'react';
import { formatNumber } from '../format';

interface Props {
  /** Events per second, one sample per second, oldest first. */
  samples: number[];
}

const W = 600;
const H = 150;
const PAD = 8;
const SLOTS = 60;
const STEP = W / (SLOTS - 1);

/** A 60 second line chart of throughput. Move the pointer over it to read exact values. */
export function ThroughputChart({ samples }: Props) {
  const [hover, setHover] = useState<number | null>(null);
  const n = samples.length;
  const peak = Math.max(10, ...samples);
  const top = peak * 1.15;

  const x = (i: number): number => W - (n - 1 - i) * STEP;
  const y = (v: number): number => H - PAD - (v / top) * (H - PAD * 2);

  const line = samples.map((v, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
  const area = n > 1 ? `${line} L${x(n - 1)},${H} L${x(0)},${H} Z` : '';

  const onMove = (e: PointerEvent<SVGSVGElement>) => {
    const box = e.currentTarget.getBoundingClientRect();
    const px = ((e.clientX - box.left) / box.width) * W;
    const index = n - 1 - Math.round((W - px) / STEP);
    setHover(Math.max(0, Math.min(n - 1, index)));
  };

  const shown = hover !== null && hover < n ? hover : null;
  const current = n > 0 ? samples[n - 1] : 0;

  return (
    <div className="chart">
      <div className="chart-head">
        <span>
          <b>{formatNumber(Math.round(current))}</b> updates a second now
        </span>
        <span>Peak {formatNumber(Math.round(Math.max(0, ...samples)))} in the last minute</span>
      </div>
      <div className="chart-body">
        <svg
          viewBox={`0 0 ${W} ${H}`}
          role="img"
          aria-label="Line chart of updates filed per second over the last minute"
          onPointerMove={onMove}
          onPointerLeave={() => setHover(null)}
        >
          <defs>
            <linearGradient id="tpFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" style={{ stopColor: 'var(--accent)', stopOpacity: 0.35 }} />
              <stop offset="100%" style={{ stopColor: 'var(--accent)', stopOpacity: 0 }} />
            </linearGradient>
          </defs>
          {[0, 0.5, 1].map((f) => (
            <line key={f} x1="0" x2={W} y1={H - PAD - f * (H - PAD * 2)} y2={H - PAD - f * (H - PAD * 2)} className="grid" />
          ))}
          {n > 1 && <path d={area} fill="url(#tpFill)" />}
          {n > 1 && <path d={line} className="chart-line" />}
          {shown !== null && (
            <g>
              <line x1={x(shown)} x2={x(shown)} y1="0" y2={H} className="cursor" />
              <circle cx={x(shown)} cy={y(samples[shown])} r="4.5" className="cursor-dot" />
            </g>
          )}
        </svg>
        {shown !== null && (
          <div className="tip" style={{ left: `${(x(shown) / W) * 100}%` }}>
            {formatNumber(Math.round(samples[shown]))} / s
            <span>{n - 1 - shown === 0 ? 'now' : `${n - 1 - shown}s ago`}</span>
          </div>
        )}
        {n < 2 && <div className="empty chart-empty">Collecting data...</div>}
      </div>
    </div>
  );
}
