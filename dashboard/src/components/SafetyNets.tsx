import { formatNumber } from '../format';
import { useView } from '../view';

interface Props {
  profiles: number;
  duplicates: number;
  deadLettered: number;
}

const icon = {
  disk: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <ellipse cx="12" cy="6" rx="7" ry="3" />
      <path d="M5 6v12c0 1.7 3.1 3 7 3s7-1.3 7-3V6" />
      <path d="M5 12c0 1.7 3.1 3 7 3s7-1.3 7-3" />
    </svg>
  ),
  twice: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="8" y="8" width="12" height="12" rx="2" />
      <path d="M4 16V6a2 2 0 0 1 2-2h10" />
      <path d="M11 14l2 2 4-4" />
    </svg>
  ),
  tray: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3 13l3-8h12l3 8" />
      <path d="M3 13v6h18v-6" />
      <path d="M3 13h5l1 3h6l1-3h5" />
    </svg>
  ),
};

/** The three promises the system keeps when things go wrong, with live counters where there is something to count. */
export function SafetyNets({ profiles, duplicates, deadLettered }: Props) {
  const { technical } = useView();
  return (
    <div className="nets">
      <div className="net">
        <span className="icon">{icon.disk}</span>
        <h3>Nothing is lost if the app restarts</h3>
        <p>
          {technical
            ? 'Ignite native persistence writes every profile to disk, with a write-ahead log. On start it reads them back.'
            : 'Credit files are saved to disk. Stop the app and start it again, and every file is still there.'}
        </p>
        <div className="count">
          <b>{formatNumber(profiles)}</b> files on record
        </div>
      </div>
      <div className="net">
        <span className="icon">{icon.twice}</span>
        <h3>Nothing is counted twice</h3>
        <p>
          {technical
            ? 'Kafka delivers at least once. Each profile remembers its last 50 event IDs, and the check runs inside the atomic update.'
            : 'If the same update arrives twice, the second copy is recognised and ignored.'}
        </p>
        <div className="count">
          <b>{formatNumber(duplicates)}</b> repeats ignored
        </div>
      </div>
      <div className="net">
        <span className="icon">{icon.tray}</span>
        <h3>Nothing disappears silently</h3>
        <p>
          {technical
            ? 'A failing record is retried 3 times, then copied to the credit-events.DLT dead-letter topic. Unreadable JSON goes there straight away.'
            : 'An update that cannot be processed is tried again, then set aside in a separate holding area to be looked at.'}
        </p>
        <div className="count">
          <b>{formatNumber(deadLettered)}</b> set aside
        </div>
      </div>
    </div>
  );
}
