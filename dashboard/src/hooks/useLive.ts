import { useEffect, useState } from 'react';
import type { LiveSnapshot } from '../types';

/**
 * Opens one long-lived connection to /api/live (Server-Sent Events). The backend pushes a fresh snapshot
 * about four times a second, so the page updates the moment something is filed instead of asking again and again.
 *
 * The browser's EventSource reconnects by itself if the connection drops. `live` is true while snapshots are
 * arriving, and the page falls back to ordinary polling while it is false.
 */
export function useLive() {
  const [snapshot, setSnapshot] = useState<LiveSnapshot | null>(null);
  const [live, setLive] = useState(false);

  useEffect(() => {
    if (typeof EventSource === 'undefined') {
      return;
    }
    const source = new EventSource('/api/live');
    source.addEventListener('snapshot', (event) => {
      try {
        setSnapshot(JSON.parse((event as MessageEvent<string>).data) as LiveSnapshot);
        setLive(true);
      } catch {
        // ignore one unreadable message and wait for the next
      }
    });
    source.onerror = () => setLive(false);
    return () => source.close();
  }, []);

  return { snapshot, live };
}
