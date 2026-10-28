export const formatNumber = (n: number): string => n.toLocaleString('en-IN');

export const formatMoney = (n: number): string =>
  '₹' + n.toLocaleString('en-IN', { maximumFractionDigits: 0 });

export function formatClock(epochMs: number): string {
  const d = new Date(epochMs);
  const pad = (n: number, width = 2) => String(n).padStart(width, '0');
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${pad(d.getMilliseconds(), 3)}`;
}

/** "org.springframework.kafka.KafkaListenerEndpointContainer#0-1-C-1" becomes "consumer-1". */
export function consumerName(thread: string): string {
  const match = /#\d+-(\d+)-C-\d+/.exec(thread);
  return match ? `consumer-${match[1]}` : thread;
}

/** "PAYMENT_ON_TIME" becomes "Payment On Time". */
export function eventLabel(type: string): string {
  return type
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

/** "PAYMENT_ON_TIME" becomes "Payment on time". */
export function plainEventLabel(type: string): string {
  switch (type) {
    case 'ACCOUNT_OPENED':
      return 'Loan opened';
    case 'ACCOUNT_CLOSED':
      return 'Loan closed';
    case 'PAYMENT_ON_TIME':
      return 'Payment on time';
    case 'PAYMENT_DELAYED':
      return 'Late payment';
    default:
      return eventLabel(type);
  }
}

/** "org.springframework.kafka.KafkaListenerEndpointContainer#0-1-C-1" becomes "Worker 1". */
export function workerName(thread: string): string {
  const match = /#\d+-(\d+)-C-\d+/.exec(thread);
  return match ? `Worker ${match[1]}` : thread;
}
