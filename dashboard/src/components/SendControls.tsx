import { useEffect, useRef, useState } from 'react';
import { api } from '../api';
import { formatNumber } from '../format';
import { useView } from '../view';

/** Buttons that ask the backend to make up credit updates and send them into the pipeline. */
export function SendControls() {
  const { technical } = useView();
  const [count, setCount] = useState(1000);
  const [borrowers, setBorrowers] = useState(200);
  const [sending, setSending] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const borrowersRef = useRef(borrowers);
  borrowersRef.current = borrowers;
  const [message, setMessage] = useState<{ text: string; isError: boolean; at: number } | null>(null);

  const send = async (n: number) => {
    setSending(true);
    try {
      const result = await api.generate(n, borrowers);
      const text = technical
        ? `Published ${formatNumber(result.published)} events to Kafka.`
        : `Sent ${formatNumber(result.published)} updates.`;
      setMessage({ text, isError: false, at: Date.now() });
    } catch {
      setMessage({ text: 'Could not send updates. Check that the app and Kafka are running.', isError: true, at: Date.now() });
    } finally {
      setSending(false);
    }
  };

  // Live stream: send a small batch every half second until the button is pressed again.
  // The cleanup function stops the timer when streaming is switched off or the component goes away.
  useEffect(() => {
    if (!streaming) {
      return;
    }
    const timer = setInterval(() => {
      api.generate(25, borrowersRef.current).catch(() => setStreaming(false));
    }, 500);
    return () => clearInterval(timer);
  }, [streaming]);

  return (
    <div className="try">
      <div className="try-row">
        {[1000, 10000, 50000].map((n) => (
          <button
            key={n}
            className={n === 10000 ? 'primary' : ''}
            disabled={sending}
            onClick={() => {
              setCount(n);
              send(n);
            }}
          >
            {sending && n === count ? <span className="spinner" aria-hidden="true" /> : null}
            Send {formatNumber(n)} updates
          </button>
        ))}
        <button className={`stream${streaming ? ' live' : ''}`} onClick={() => setStreaming((on) => !on)}>
          {streaming ? 'Stop live stream' : 'Start live stream'}
        </button>
      </div>
      <details className="custom">
        <summary>Choose your own numbers</summary>
        <div className="controls">
          <label htmlFor="count">
            Updates to send
            <input id="count" type="number" min={1} max={100000} value={count} onChange={(e) => setCount(Number(e.target.value))} />
          </label>
          <label htmlFor="pool">
            Number of people
            <input id="pool" type="number" min={1} value={borrowers} onChange={(e) => setBorrowers(Number(e.target.value))} />
          </label>
          <button disabled={sending} onClick={() => send(count)}>
            Send
          </button>
        </div>
      </details>
      <div key={message?.at} className={message?.isError ? 'toast err' : 'toast'} aria-live="polite">
        {message?.text}
      </div>
    </div>
  );
}
