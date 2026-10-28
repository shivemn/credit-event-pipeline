// The shapes of the JSON returned by the Spring Boot API.
// If the Java side changes, change these to match: TypeScript will then point out every place that breaks.

export interface Stats {
  publishedEvents: number;
  processedEvents: number;
  cachedProfiles: number;
  /** Events ignored because they had already been applied. Missing on older versions of the backend. */
  duplicateEvents?: number;
  /** Events moved to the dead-letter topic. Missing on older versions of the backend. */
  deadLetteredEvents?: number;
  /** Events kept in PostgreSQL. Missing when the archive is switched off. */
  archivedEvents?: number;
  /** Partition number (as a string key) to events processed. */
  processedPerPartition: Record<string, number>;
}

export interface RecentEvent {
  borrowerId: string;
  type: string;
  amount: number;
  partition: number;
  offset: number;
  thread: string;
  processedAtEpochMs: number;
}

export interface Profile {
  borrowerId: string;
  activeAccounts: number;
  closedAccounts: number;
  onTimePayments: number;
  delayedPayments: number;
  totalExposure: number;
  eventsApplied: number;
  lastEventAtEpochMs: number;
  paymentReliability: number;
}

export interface Portfolio {
  borrowers: number;
  totalExposure: number;
  onTimePayments: number;
  delayedPayments: number;
  /** Share of payments made on time, 0 to 1. */
  onTimeRate: number;
}

export interface AtRiskBorrower {
  borrowerId: string;
  delayedPayments: number;
  onTimePayments: number;
}

export interface Analytics {
  portfolio: Portfolio;
  atRisk: AtRiskBorrower[];
}

/** One message from the live stream (/api/live): the headline numbers plus the latest filed updates. */
export interface LiveSnapshot {
  stats: Stats;
  recent: RecentEvent[];
}

export interface ReplayStatus {
  running: boolean;
  rate: number;
  sent: number;
  lap: number;
  file: string;
  fileFound: boolean;
  /** True when the file is the bundled made-up sample rather than a real download. */
  sampleData: boolean;
  error: string | null;
}

export interface ArchiveStatus {
  enabled: boolean;
  storedEvents: number;
  rebuildAllowed: boolean;
  rebuild: { running: boolean; replayed: number; error: string | null } | null;
}

export interface HistoryRow {
  eventId: string;
  type: string;
  amount: number;
  occurredAtEpochMs: number;
  partition: number | null;
  offset: number | null;
}
