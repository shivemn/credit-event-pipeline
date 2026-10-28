import { useState } from 'react';
import { api } from '../api';
import { formatNumber } from '../format';
import { usePolling } from '../hooks/usePolling';
import { useView } from '../view';

const RATES = [100, 500, 2000];

/** Starts and stops the replay of a public loan dataset, as if banks were reporting loans live. */
export function ReplayControl() {
  const { technical } = useView();
  const [rate, setRate] = useState(500);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const { data: status } = usePolling(api.replayStatus, 1500, failed);

  const toggle = async () => {
    setBusy(true);
    setFailed(false);
    try {
      if (status?.running) {
        await api.stopReplay();
      } else {
        await api.startReplay(rate);
      }
    } catch {
      setFailed(true);
    } finally {
      setBusy(false);
    }
  };

  const running = status?.running ?? false;

  return (
    <div className="replay">
      <h3>{technical ? 'Replay a loan dataset' : 'Replay real loan data'}</h3>
      <p>
        {technical
          ? 'Reads a loans CSV (Lending Club columns) row by row and publishes each loan as ACCOUNT_OPENED, payment and ACCOUNT_CLOSED events to Kafka.'
          : 'Plays a public file of loans into the system one by one, like banks reporting them live. Each loan becomes a short story: opened, paid, maybe closed.'}
      </p>
      <div className="try-row">
        {RATES.map((r) => (
          <button key={r} className={!running && r === rate ? 'primary' : ''} disabled={running || busy} onClick={() => setRate(r)}>
            {formatNumber(r)} a second
          </button>
        ))}
        <button className={`stream${running ? ' live' : ''}`} disabled={busy} onClick={toggle}>
          {running ? 'Stop replay' : 'Start replay'}
        </button>
      </div>
      {status && (
        <div className="toast" aria-live="polite">
          {running
            ? `Replaying at ${formatNumber(status.rate)} a second. ${formatNumber(status.sent)} sent${status.lap > 0 ? `, round ${status.lap + 1} of the file` : ''}.`
            : status.sent > 0
              ? `Stopped after sending ${formatNumber(status.sent)}.`
              : ''}
        </div>
      )}
      {status?.error && <div className="toast err">{status.error}</div>}
      {failed && <div className="toast err">Could not reach the app. Is it started?</div>}
      {status?.sampleData && (
        <p className="note">
          Using the bundled sample file (<code>{status.file}</code>), which is made up. The README shows how to replay
          the real public dataset instead.
        </p>
      )}
    </div>
  );
}
