import { useEffect, useRef, useState } from 'react';
import { formatNumber } from '../format';
import { useView } from '../view';

interface Props {
  counts: Record<string, number>;
  /** Updates filed per second. Drives how many dots travel and how fast. */
  rate: number;
}

const LANES = [50, 120, 190];
const MID = 120;

/** A route from the front desk, through one lane and its worker, into the filing cabinet. */
const route = (y: number): string =>
  `M150,${MID} C210,${MID} 210,${y} 270,${y} L650,${y} C710,${y} 710,${MID} 770,${MID}`;

function usePartitionActivity(counts: Record<string, number>): Record<string, boolean> {
  const previous = useRef<Record<string, number>>({});
  const [active, setActive] = useState<Record<string, boolean>>({});
  const signature = JSON.stringify(counts);

  useEffect(() => {
    const next: Record<string, boolean> = {};
    for (const key of Object.keys(counts)) {
      next[key] = counts[key] > (previous.current[key] ?? counts[key]);
    }
    previous.current = { ...counts };
    setActive(next);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature]);

  return active;
}

/**
 * The pipeline drawn as moving dots. A dot travels along a lane only while that lane is filing updates,
 * and more, faster dots mean a higher rate. Labels switch between plain English and the real technology names.
 */
export function FlowDiagram({ counts, rate }: Props) {
  const { technical } = useView();
  const active = usePartitionActivity(counts);
  const level = rate <= 0 ? 0 : Math.min(3, 1 + Math.floor(Math.log10(rate + 1)));
  const duration = Math.max(0.9, 2.6 - level * 0.55);
  const anyActive = LANES.some((_, i) => active[String(i)]);

  const label = technical
    ? { front: 'REST API', frontSub: 'POST /api/events', lanes: 'Kafka topic', workers: 'Consumer threads', store: 'Ignite cache', storeSub: 'borrower profiles' }
    : { front: 'Front desk', frontSub: 'banks send updates', lanes: 'Sorting lanes', workers: 'Workers', store: 'Credit files', storeSub: 'saved on disk' };

  return (
    <svg
      className="flow"
      viewBox="0 0 920 240"
      role="img"
      aria-label={`Diagram: ${label.front}, then three ${technical ? 'Kafka partitions' : 'sorting lanes'}, then three ${technical ? 'consumers' : 'workers'}, then ${label.store}`}
    >
      {LANES.map((y, i) => (
        <path key={`line-${i}`} d={route(y)} className={`flow-line${active[String(i)] ? ' on' : ''}`} style={{ stroke: `var(--s${i})` }} />
      ))}

      <g className="node">
        <rect x="20" y="90" width="130" height="60" rx="12" />
        <text x="85" y="117" textAnchor="middle" className="t1">{label.front}</text>
        <text x="85" y="135" textAnchor="middle" className="t2">{label.frontSub}</text>
      </g>

      <text x="330" y="18" textAnchor="middle" className="t2">{label.lanes}</text>
      <text x="585" y="18" textAnchor="middle" className="t2">{label.workers}</text>

      {LANES.map((y, i) => {
        const on = !!active[String(i)];
        return (
          <g key={`lane-${i}`}>
            <g className={`node${on ? ' on' : ''}`} style={{ ['--node' as string]: `var(--s${i})` }}>
              <rect x="270" y={y - 24} width="120" height="48" rx="10" />
              <text x="330" y={y - 4} textAnchor="middle" className="t1">{technical ? `Partition ${i}` : `Lane ${i}`}</text>
              <text x="330" y={y + 14} textAnchor="middle" className="t2">{formatNumber(counts[String(i)] ?? 0)} filed</text>
            </g>
            <g className={`node${on ? ' on' : ''}`} style={{ ['--node' as string]: `var(--s${i})` }}>
              <rect x="520" y={y - 24} width="130" height="48" rx="10" />
              <text x="585" y={y + 5} textAnchor="middle" className="t1">{technical ? `consumer-${i}` : `Worker ${i}`}</text>
            </g>
          </g>
        );
      })}

      <g className={`node ignite${anyActive ? ' on' : ''}`} style={{ ['--node' as string]: 'var(--stage-ink)' }}>
        <rect x="770" y="90" width="130" height="60" rx="12" />
        <text x="835" y="117" textAnchor="middle" className="t1">{label.store}</text>
        <text x="835" y="135" textAnchor="middle" className="t2">{label.storeSub}</text>
      </g>

      {LANES.map((y, i) =>
        active[String(i)]
          ? Array.from({ length: level }, (_, k) => (
              <circle key={`dot-${i}-${k}-${level}`} r="4.5" className="dot-particle" style={{ fill: `var(--s${i})` }}>
                <animateMotion
                  dur={`${duration}s`}
                  begin={`-${((k * duration) / level).toFixed(2)}s`}
                  repeatCount="indefinite"
                  path={route(y)}
                />
              </circle>
            ))
          : null,
      )}
    </svg>
  );
}
