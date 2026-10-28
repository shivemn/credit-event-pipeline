const TERMS: [string, string][] = [
  ['Credit bureau', 'A company that collects loan and payment history so lenders can judge how risky a borrower is.'],
  ['Credit file', 'One person’s summary, built from every update about them. The system calls it a profile.'],
  ['Update', 'One fact reported by a bank: a loan opened, a payment on time, a late payment, or a loan closed. The system calls it an event.'],
  ['Apache Kafka', 'Software that holds updates in order and hands them out to programs that process them. It lets a sudden rush wait its turn.'],
  ['Lane', 'One of the separate lines inside Kafka (a partition). More lanes mean more workers can work at once.'],
  ['Worker', 'A program thread that takes updates from a lane and processes them (a consumer).'],
  ['Apache Ignite', 'A database that keeps data in memory for speed and can spread it across several computers. GridGain is the commercial product built on it.'],
  ['Atomic update', 'A change that either happens completely or not at all, with nobody able to interfere halfway through (an entry processor).'],
  ['Set aside', 'Where updates go when they keep failing: a separate Kafka topic, called a dead-letter topic.'],
  ['Prometheus and Grafana', 'Tools that record numbers over time and draw charts of them. Grafana runs at localhost:3000 beside this page.'],
];

/** Plain definitions of every term used on the page. */
export function Glossary() {
  return (
    <dl className="glossary">
      {TERMS.map(([term, meaning]) => (
        <div key={term}>
          <dt>{term}</dt>
          <dd>{meaning}</dd>
        </div>
      ))}
    </dl>
  );
}
