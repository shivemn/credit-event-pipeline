import { api } from '../api';
import { formatMoney, formatNumber } from '../format';
import { usePolling } from '../hooks/usePolling';

interface Props {
  selected: string | null;
  onSelect: (borrowerId: string) => void;
}

/** The credit files with the most updates. Click a row to open that person's file. */
export function TopBorrowers({ selected, onSelect }: Props) {
  const { data: top } = usePolling(() => api.topProfiles(8), 3000);

  if (!top || top.length === 0) {
    return <div className="empty">No credit files yet. Send some updates and the busiest people appear here.</div>;
  }
  const max = Math.max(...top.map((p) => p.eventsApplied), 1);

  return (
    <table>
      <thead>
        <tr>
          <th>Person</th>
          <th className="num">Updates</th>
          <th className="share-col" aria-label="Share of the busiest file"></th>
          <th className="num">Amount owed</th>
        </tr>
      </thead>
      <tbody>
        {top.map((p) => (
          <tr
            key={p.borrowerId}
            className={`clickable${selected === p.borrowerId ? ' selected' : ''}`}
            onClick={() => onSelect(p.borrowerId)}
          >
            <td>{p.borrowerId}</td>
            <td className="num">{formatNumber(p.eventsApplied)}</td>
            <td className="share-col">
              <div className="bar thin">
                <i style={{ width: `${Math.round((p.eventsApplied * 100) / max)}%` }} />
              </div>
            </td>
            <td className="num">{formatMoney(p.totalExposure)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
