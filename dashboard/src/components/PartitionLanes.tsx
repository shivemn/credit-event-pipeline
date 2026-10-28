import { useEffect, useRef, useState } from 'react';
import { formatNumber } from '../format';
import { useView } from '../view';

interface Props {
  counts: Record<string, number>;
}

/** One bar per lane. A bar shimmers while its lane is still receiving work. */
export function PartitionLanes({ counts }: Props) {
  const { technical } = useView();
  const previous = useRef<Record<string, number>>({});
  const [hot, setHot] = useState<Record<string, boolean>>({});
  const signature = JSON.stringify(counts);

  useEffect(() => {
    const next: Record<string, boolean> = {};
    for (const key of Object.keys(counts)) {
      next[key] = counts[key] > (previous.current[key] ?? counts[key]);
    }
    previous.current = { ...counts };
    setHot(next);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature]);

  const partitions = Object.keys(counts).sort((a, b) => Number(a) - Number(b));
  if (partitions.length === 0) {
    return <div className="empty">No updates yet. Send some at the top of the page.</div>;
  }
  const max = Math.max(...partitions.map((p) => counts[p]), 1);
  const total = Math.max(partitions.reduce((sum, p) => sum + counts[p], 0), 1);

  return (
    <>
      {partitions.map((partition, index) => (
        <div className="lane" key={partition} style={{ ['--lane' as string]: `var(--l${index % 3})` }}>
          <div className="row">
            <span>
              <span className="swatch" /> {technical ? `Partition ${partition}` : `Lane ${partition}`}
            </span>
            <span>
              {formatNumber(counts[partition])} filed, {Math.round((counts[partition] * 100) / total)}% of the total
            </span>
          </div>
          <div className={`bar lane-bar${hot[partition] ? ' hot' : ''}`}>
            <i style={{ width: `${Math.round((counts[partition] * 100) / max)}%` }} />
          </div>
        </div>
      ))}
    </>
  );
}
