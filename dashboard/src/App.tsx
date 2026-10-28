import { useState } from 'react';
import { api } from './api';
import { formatNumber } from './format';
import { useLive } from './hooks/useLive';
import { usePolling } from './hooks/usePolling';
import { useRate } from './hooks/useRate';
import { useHistory } from './hooks/useHistory';
import { EventFeed } from './components/EventFeed';
import { FlowDiagram } from './components/FlowDiagram';
import { Glossary } from './components/Glossary';
import { HowItWorks } from './components/HowItWorks';
import { Logo } from './components/Logo';
import { PartitionLanes } from './components/PartitionLanes';
import { ReplayControl } from './components/ReplayControl';
import { PortfolioPanel } from './components/PortfolioPanel';
import { ProfilePanel } from './components/ProfilePanel';
import { SafetyNets } from './components/SafetyNets';
import { SendControls } from './components/SendControls';
import { StatTile } from './components/StatTile';
import { ThemeToggle } from './components/ThemeToggle';
import { ThroughputChart } from './components/ThroughputChart';
import { TopBorrowers } from './components/TopBorrowers';
import { ViewToggle } from './components/ViewToggle';
import { useView } from './view';

const NO_COUNTS: Record<string, number> = {};
// Grafana only exists on the owner's computer, so visitors on a shared link should not see a dead link.
const isLocal = ['localhost', '127.0.0.1'].includes(window.location.hostname);

export default function App() {
  const { technical } = useView();
  // The backend pushes new numbers over one open connection. If that is not working, ask every second instead.
  const { snapshot, live } = useLive();
  const { data: polledStats, error: statsError } = usePolling(api.stats, 1000, null, !live);
  const { data: polledRecent } = usePolling(api.recent, 1000, null, !live);
  const stats = live && snapshot ? snapshot.stats : polledStats;
  const recent = live && snapshot ? snapshot.recent : polledRecent;
  const rate = useRate(stats?.processedEvents, stats);
  const history = useHistory(rate);
  const [selected, setSelected] = useState<string | null>(null);

  const published = stats?.publishedEvents ?? 0;
  const processed = stats?.processedEvents ?? 0;
  const waiting = Math.max(0, published - processed);
  const connected = live || (stats !== null && statsError === null);
  const counts = stats?.processedPerPartition ?? NO_COUNTS;

  // Choosing a person anywhere on the page opens their file, so bring that file into view.
  const openFile = (borrowerId: string | null) => {
    setSelected(borrowerId);
    if (borrowerId) {
      document.getElementById('profile')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  };

  return (
    <div className="wrap">
      <header className="top">
        <div className="brand">
          <Logo />
          Credit Event Pipeline
        </div>
        <div className="top-right">
          <ViewToggle />
          <ThemeToggle />
          <div className="status" role="status">
            <span className={`dot ${connected ? 'on' : 'off'}`} />
            {connected ? (live ? 'Live' : 'Running') : 'Cannot reach the app. Is it started?'}
          </div>
        </div>
      </header>

      <section className="hero">
        <div>
          <h1>What happens when a bank reports a late payment?</h1>
          <p>
            Banks send thousands of updates about loans every second. This demo shows how a credit bureau takes
            them in, keeps every person&rsquo;s credit file correct, and carries on through crashes, repeats and bad
            data. Everything on this page is made up.
          </p>
        </div>
        <div className="try-card">
          <h2>Try it</h2>
          <p>Send some made-up updates, then watch them travel through the system below.</p>
          <SendControls />
          <ReplayControl />
        </div>
      </section>

      <section>
        <h2>Watch it work</h2>
        <p className="lead">
          {technical
            ? 'Events enter through the REST API, wait in Kafka partitions, are processed by consumer threads and end up in Ignite. Dots move only while that lane is processing.'
            : 'Updates come in on the left, wait in one of three lanes, get picked up by a worker, and end up in the person’s credit file on the right. Dots move only while a lane is busy.'}
        </p>
        <div className="panel scroll">
          <FlowDiagram counts={counts} rate={rate} />
        </div>
      </section>

      <section>
        <h2>How it works, step by step</h2>
        <p className="lead">Four stages, from a bank&rsquo;s message to an up-to-date credit file.</p>
        <HowItWorks />
      </section>

      <section>
        <h2>The numbers right now</h2>
        <p className="lead">These change as you send updates. Try the 50,000 button to see the system under load.</p>
        <div className="tiles">
          <StatTile label={technical ? 'Published to Kafka' : 'Updates sent'} value={published} unit="since the app started" />
          <StatTile label={technical ? 'Processed' : 'Updates filed'} value={processed} unit={technical ? 'applied to Ignite' : 'added to a credit file'} />
          <StatTile
            label={technical ? 'In flight' : 'Waiting in line'}
            value={waiting}
            unit="sent, not yet filed"
            progress={published > 0 ? processed / published : 0}
          />
          <StatTile label="Speed" value={Math.round(rate)} unit="updates filed per second" />
          <StatTile label={technical ? 'Profiles in cache' : 'People on file'} value={stats?.cachedProfiles ?? 0} unit={technical ? 'borrowers' : 'with a credit file'} format={formatNumber} />
        </div>
      </section>

      <section className="cols even">
        <div>
          <h2>Speed over the last minute</h2>
          <p className="lead">How many updates were filed each second.</p>
          <div className="panel">
            <ThroughputChart samples={history} />
          </div>
        </div>
        <div>
          <h2>{technical ? 'Work per Kafka partition' : 'Work per lane'}</h2>
          <p className="lead">The work should spread evenly, so no single lane becomes a bottleneck.</p>
          <div className="panel">
            <PartitionLanes counts={counts} />
          </div>
        </div>
      </section>

      <section>
        <h2>What the credit files add up to</h2>
        <p className="lead">Totals across every person on file, worked out inside the database as updates arrive.</p>
        <PortfolioPanel onSelect={openFile} />
      </section>

      <section>
        <h2>Look at one credit file</h2>
        <p className="lead">Pick someone from the lists to open their file. It keeps updating while updates arrive.</p>
        <div className="cols">
          <div>
            <h3 style={{ marginBottom: 8 }}>Most active people</h3>
            <div className="panel">
              <TopBorrowers selected={selected} onSelect={openFile} />
            </div>
          </div>
          <div>
            <h3 style={{ marginBottom: 8 }}>Credit file</h3>
            <ProfilePanel borrowerId={selected} onLookup={setSelected} />
          </div>
        </div>
      </section>

      <section>
        <h2>The latest updates filed</h2>
        <p className="lead">Each row is one update, and which worker filed it.</p>
        <div className="panel scroll">
          <EventFeed events={recent ?? []} onSelect={openFile} />
        </div>
      </section>

      <section>
        <h2>What if something goes wrong?</h2>
        <p className="lead">Real systems crash, repeat messages and receive bad data. This one is built for that.</p>
        <SafetyNets
          profiles={stats?.cachedProfiles ?? 0}
          duplicates={stats?.duplicateEvents ?? 0}
          deadLettered={stats?.deadLetteredEvents ?? 0}
        />
      </section>

      <details className="words">
        <summary>Words used on this page</summary>
        <Glossary />
      </details>

      <footer>
        <div>
          Built with Spring Boot, Apache Kafka, Apache Ignite and React.
          {isLocal && (
            <>
              {' '}Charts of the same system over time are in{' '}
              <a href="http://localhost:3000/d/credit-pipeline" target="_blank" rel="noreferrer">
                Grafana
              </a>
              , which runs on this computer.
            </>
          )}
        </div>
        <div>People are anonymous made-up IDs. Updates are either randomly generated or replayed from a public loan dataset. No employer or client data is used.</div>
      </footer>
    </div>
  );
}
