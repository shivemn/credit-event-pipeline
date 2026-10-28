/** Three lanes of different lengths, in the lane colours used across the page. */
export function Logo() {
  return (
    <svg className="logo" viewBox="0 0 32 32" aria-hidden="true">
      <rect x="3" y="6" width="22" height="5" rx="2.5" style={{ fill: 'var(--l0)' }} />
      <rect x="3" y="13.5" width="26" height="5" rx="2.5" style={{ fill: 'var(--l1)' }} />
      <rect x="3" y="21" width="16" height="5" rx="2.5" style={{ fill: 'var(--l2)' }} />
    </svg>
  );
}
