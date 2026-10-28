import { api } from '../api';
import { formatClock, formatMoney, plainEventLabel } from '../format';
import { usePolling } from '../hooks/usePolling';
import { useView } from '../view';

interface Props {
  borrowerId: string | null;
}

/** The permanent timeline of one person, read from the PostgreSQL archive (newest first). */
export function HistoryPanel({ borrowerId }: Props) {
  const { technical } = useView();
  const { data: rows } = usePolling(
    () => (borrowerId ? api.history(borrowerId, 12) : Promise.resolve(null)),
    3000,
    borrowerId,
  );

  if (!borrowerId) {
    return null;
  }

  return (
    <div className="panel history">
      <h3>{technical ? 'History in PostgreSQL' : 'Full history'}</h3>
      <p className="note">
        {technical
          ? 'Every event for this borrower, read from the credit_events table. It is kept even if Ignite is emptied.'
          : 'Every update ever filed for this person, kept permanently. The credit file above is the summary; this is the record behind it.'}
      </p>
      {rows === null && <div className="empty">Loading the history…</div>}
      {rows && rows.length === 0 && <div className="empty">No history saved yet. It appears a moment after an update is filed.</div>}
      {rows && rows.length > 0 && (
        <div className="scroll">
          <table>
            <thead>
              <tr>
                <th>Time</th>
                <th>What happened</th>
                <th className="num">Amount</th>
                {technical && <th>Kafka</th>}
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.eventId}>
                  <td>{formatClock(row.occurredAtEpochMs)}</td>
                  <td>{technical ? row.type : plainEventLabel(row.type)}</td>
                  <td className="num">{formatMoney(row.amount)}</td>
                  {technical && (
                    <td>
                      p{row.partition ?? '?'} @ {row.offset ?? '?'}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
