import type { Analytics, Profile, RecentEvent, ReplayStatus, Stats } from './types';

export class ApiError extends Error {
  status: number;
  constructor(status: number) {
    super(`HTTP ${status}`);
    this.status = status;
  }
}

async function getJSON<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) {
    throw new ApiError(response.status);
  }
  return (await response.json()) as T;
}

export const api = {
  stats: () => getJSON<Stats>('/api/stats'),
  analytics: (limit: number) => getJSON<Analytics>(`/api/analytics?limit=${limit}`),
  recent: () => getJSON<RecentEvent[]>('/api/recent'),
  topProfiles: (limit: number) => getJSON<Profile[]>(`/api/profiles?limit=${limit}`),
  profile: (borrowerId: string) => getJSON<Profile>(`/api/profiles/${encodeURIComponent(borrowerId)}`),
  replayStatus: () => getJSON<ReplayStatus>('/api/replay'),
  startReplay: (rate: number) => getJSON<ReplayStatus>(`/api/replay/start?rate=${rate}`, { method: 'POST' }),
  stopReplay: () => getJSON<ReplayStatus>('/api/replay/stop', { method: 'POST' }),
  generate: (count: number, borrowers: number) =>
    getJSON<{ published: number }>(`/api/events/generate?count=${count}&borrowers=${borrowers}`, {
      method: 'POST',
    }),
};
