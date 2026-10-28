import { useView } from '../view';

const STEPS = [
  {
    title: 'A bank reports something',
    plain: 'A loan is opened, a payment arrives on time or late, or a loan is closed. In this demo the updates are made up.',
    tech: 'A REST endpoint accepts the update, or a generator creates random ones. Spring Boot, Java 17.',
    under: 'REST API',
  },
  {
    title: 'Updates wait in sorted lanes',
    plain:
      'Updates line up in three lanes, so a sudden rush never overwhelms the system. One person always uses the same lane, which keeps their updates in order.',
    tech: 'Events go to a Kafka topic with 3 partitions, keyed by borrower ID, so one borrower stays on one partition and keeps its order.',
    under: 'Apache Kafka',
  },
  {
    title: 'Workers file each update',
    plain: 'Three workers each take updates from their own lane and add them to the right person’s credit file, all at the same time.',
    tech: 'Three consumer threads in one consumer group, one per partition, read events and call the cache.',
    under: 'Consumer threads',
  },
  {
    title: 'Credit files stay up to date',
    plain:
      'Each file is kept in memory for fast answers and saved to disk. Changing a file is one indivisible step, so two workers can never overwrite each other.',
    tech: 'Apache Ignite holds a CreditProfile per borrower. An atomic entry processor updates it, with persistence and one backup copy.',
    under: 'Apache Ignite',
  },
];

/** The four stages, in order. Each shows the plain explanation, or the technical one in the Technical view. */
export function HowItWorks() {
  const { technical } = useView();
  return (
    <ol className="steps">
      {STEPS.map((step) => (
        <li key={step.title}>
          <h3>{step.title}</h3>
          <p>{technical ? step.tech : step.plain}</p>
          <span className="under">Under the hood: {step.under}</span>
        </li>
      ))}
    </ol>
  );
}
