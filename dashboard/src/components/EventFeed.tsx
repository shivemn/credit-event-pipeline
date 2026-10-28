import type { RecentEvent } from '../types';
import { consumerName, formatClock, formatMoney, formatNumber, plainEventLabel, workerName } from '../format';
import { useView } from '../view';

interface Props {
  events: RecentEvent[];
  onSelect: (borrowerId: string) => void;
}

const chipClass = (type: string): string =>
  type === 'ACCOUNT_OPENED' ? 'OPENED' : type === 'PAYMENT_ON_TIME' ? 'ONTIME' : type === 'PAYMENT_DELAYED' ? 'DELAYED' : 'CLOSED';

/** The most recently filed updates, newest first. Click a person to open their credit file. */
export function EventFeed({ events, onSelect }: Props) {
  const { technical } = useView();

  if (events.length === 0) {
    return <div className="empty">Nothing filed yet. Send some updates at the top of the page.</div>;
  }
  return (
    <table>
      <thead>
        <tr>
          <th>Time</th>
          <th>Person</th>
          <th>What happened</th>
          <th className="num">Amount</th>
          <th>Handled by</th>
          {technical && <th className="num">Partition</th>}
          {technical && <th className="num">Offset</th>}
          {technical && <th>Thread</th>}
        </tr>
      </thead>
      <tbody>
        {events.slice(0, 12).map((e) => (
          // React needs a stable key to tell list rows apart. A partition plus offset is unique in Kafka.
          <tr key={`${e.partition}-${e.offset}`} className="row-in">
            <td>{formatClock(e.processedAtEpochMs)}</td>
            <td className="link" onClick={() => onSelect(e.borrowerId)}>
              {e.borrowerId}
            </td>
            <td>
              <span className={`chip ${chipClass(e.type)}`}>{plainEventLabel(e.type)}</span>
            </td>
            <td className="num">{formatMoney(e.amount)}</td>
            <td>{technical ? consumerName(e.thread) : workerName(e.thread)}</td>
            {technical && <td className="num">{e.partition}</td>}
            {technical && <td className="num">{formatNumber(e.offset)}</td>}
            {technical && <td className="mono">{e.thread}</td>}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
