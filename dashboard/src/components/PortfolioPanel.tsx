import { api } from '../api';
import { formatMoney, formatNumber } from '../format';
import { usePolling } from '../hooks/usePolling';
import { useView } from '../view';

interface Props {
  onSelect: (borrowerId: string) => void;
}

/** What all the credit files add up to, plus the people who pay late more often than on time. */
export function PortfolioPanel({ onSelect }: Props) {
  const { technical } = useView();
  const { data } = usePolling(() => api.analytics(5), 3000);

  const portfolio = data?.portfolio;
  const atRisk = data?.atRisk ?? [];
  const onTime = portfolio?.onTimePayments ?? 0;
  const late = portfolio?.delayedPayments ?? 0;
  const payments = onTime + late;
  const onTimePct = payments === 0 ? 0 : Math.round((onTime * 100) / payments);

  return (
    <div className="cols">
      <div className="panel">
        <div className="kv big">
          <div>
            <span>People on file</span>
            <b>{formatNumber(portfolio?.borrowers ?? 0)}</b>
          </div>
          <div>
            <span>Total amount owed</span>
            <b>{formatMoney(portfolio?.totalExposure ?? 0)}</b>
          </div>
        </div>
        <div className="lane" style={{ marginTop: 18 }}>
          <div className="row">
            <span>Payments made on time</span>
            <span>{payments === 0 ? 'No payments yet' : `${onTimePct}% on time, ${100 - onTimePct}% late`}</span>
          </div>
          <div className="split" aria-hidden="true">
            <i className="good" style={{ width: `${payments === 0 ? 0 : onTimePct}%` }} />
            <i className="warn" style={{ width: `${payments === 0 ? 0 : 100 - onTimePct}%` }} />
          </div>
          <div className="row legend">
            <span>{formatNumber(onTime)} on time</span>
            <span>{formatNumber(late)} late</span>
          </div>
        </div>
        {technical && <p className="note">Calculated inside Ignite with SQL: COUNT and SUM over the CreditProfile table.</p>}
      </div>
      <div className="panel">
        <h3>People to watch</h3>
        <p className="hint">People who have paid late more often than on time.</p>
        {atRisk.length === 0 ? (
          <div className="empty">No one yet. As payments arrive, people with more late than on-time payments show up here.</div>
        ) : (
          <ul className="watch">
            {atRisk.map((p) => (
              <li key={p.borrowerId}>
                <button className="linklike" onClick={() => onSelect(p.borrowerId)}>
                  {p.borrowerId}
                </button>
                <span>
                  {p.delayedPayments} late, {p.onTimePayments} on time
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
