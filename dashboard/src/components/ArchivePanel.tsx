import { useState } from 'react';
import { api } from '../api';
import { formatNumber } from '../format';
import type { ArchiveStatus } from '../types';
import { useView } from '../view';

interface Props {
  archive: ArchiveStatus | null;
  /** Live count from the push stream, so the number moves as fast as the pipeline does. */
  stored: number | undefined;
}

/** Explains the permanent record in PostgreSQL, and (only when the owner allows it) rebuilds credit files from it. */
export function ArchivePanel({ archive, stored }: Props) {
  const { technical } = useView();
  const [failed, setFailed] = useState(false);

  if (!archive || !archive.enabled) {
    return null;
  }

  const rebuild = archive.rebuild;
  const running = rebuild?.running ?? false;

  const startRebuild = async () => {
    const sure = window.confirm(
      'This empties every credit file and rebuilds them from the saved history. Stop any replay first. Continue?',
    );
    if (!sure) {
      return;
    }
    setFailed(false);
    try {
      await api.rebuild();
    } catch {
      setFailed(true);
    }
  };

  return (
    <section>
      <h2>{technical ? 'The permanent record' : 'Every update is kept forever'}</h2>
      <p className="lead">
        {technical
          ? 'Kafka is the live stream, Ignite is the fast copy of each credit file, and PostgreSQL is the permanent record. A separate consumer group writes every event to it in batches, so it never slows the main path.'
          : 'The fast credit files can be rebuilt at any time, because every update is also saved in a normal database, one row per update.'}
      </p>
      <div className="panel archive">
        <div className="archive-count">
          <b>{formatNumber(stored ?? archive.storedEvents)}</b>
          <span>{technical ? 'events stored in PostgreSQL' : 'updates saved in the database'}</span>
        </div>
        <div className="archive-side">
          {archive.rebuildAllowed ? (
            <>
              <button className="primary" disabled={running} onClick={startRebuild}>
                {running ? 'Rebuilding…' : technical ? 'Rebuild Ignite from PostgreSQL' : 'Rebuild all credit files from the history'}
              </button>
              <div className="toast" aria-live="polite">
                {running
                  ? `Re-applied ${formatNumber(rebuild?.replayed ?? 0)} updates so far.`
                  : rebuild && rebuild.replayed > 0
                    ? `Done. Re-applied ${formatNumber(rebuild.replayed)} updates.`
                    : ''}
              </div>
              {(failed || rebuild?.error) && <div className="toast err">{rebuild?.error ?? 'Could not start the rebuild.'}</div>}
            </>
          ) : (
            <p className="note">
              {technical
                ? 'Rebuilding Ignite from this table is switched off on this server (pipeline.admin.rebuild-enabled).'
                : 'Rebuilding the credit files from this history is switched off on this server.'}
            </p>
          )}
        </div>
      </div>
    </section>
  );
}
