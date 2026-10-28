import { useEffect, useState } from 'react';
import { api, ApiError } from '../api';
import { formatMoney, formatNumber } from '../format';
import { usePolling } from '../hooks/usePolling';

interface Props {
  borrowerId: string | null;
  onLookup: (borrowerId: string | null) => void;
}

/** Open one person's credit file and keep it refreshing live while updates arrive. */
export function ProfilePanel({ borrowerId, onLookup }: Props) {
  const [input, setInput] = useState('');

  // When a person is chosen elsewhere on the page, show that ID in the text box too.
  useEffect(() => {
    setInput(borrowerId ?? '');
  }, [borrowerId]);

  const { data: profile, error } = usePolling(
    () => (borrowerId ? api.profile(borrowerId) : Promise.resolve(null)),
    1000,
    borrowerId,
  );

  const notFound = error instanceof ApiError && error.status === 404;
  const reliability = profile ? Math.round(profile.paymentReliability * 100) : 0;

  return (
    <div className="panel" id="profile">
      <form
        className="lookup"
        onSubmit={(e) => {
          e.preventDefault();
          onLookup(input.trim() || null);
        }}
      >
        <input
          type="text"
          placeholder="e.g. B000007"
          aria-label="Person ID"
          value={input}
          onChange={(e) => setInput(e.target.value)}
        />
        <button className="primary" type="submit">
          Open file
        </button>
      </form>

      {!borrowerId && (
        <div className="empty">
          Type a person's ID, for example B000007, or click a name anywhere on the page. The file updates live.
        </div>
      )}
      {borrowerId && notFound && (
        <div className="empty">There is no credit file for {borrowerId} yet. That person has not had any updates.</div>
      )}
      {borrowerId && profile && (
        <div className="profile-in" key={profile.borrowerId}>
          <h4>{profile.borrowerId}</h4>
          <div className="kv">
            <div><span>Open loans</span><b>{formatNumber(profile.activeAccounts)}</b></div>
            <div><span>Closed loans</span><b>{formatNumber(profile.closedAccounts)}</b></div>
            <div><span>Payments on time</span><b>{formatNumber(profile.onTimePayments)}</b></div>
            <div><span>Late payments</span><b>{formatNumber(profile.delayedPayments)}</b></div>
            <div><span>Amount owed</span><b>{formatMoney(profile.totalExposure)}</b></div>
            <div><span>Updates filed</span><b>{formatNumber(profile.eventsApplied)}</b></div>
          </div>
          <div className="lane" style={{ marginTop: 14 }}>
            <div className="row">
              <span>Pays on time</span>
              <span>{reliability}% of payments</span>
            </div>
            <div className="bar">
              <i style={{ width: `${reliability}%` }} />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
