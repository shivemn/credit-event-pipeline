import { useView } from '../view';

/** Two buttons that switch the whole page between plain English and the real technology names. */
export function ViewToggle() {
  const { technical, setView } = useView();
  return (
    <div className="segmented" role="group" aria-label="Wording">
      <button aria-pressed={!technical} className={!technical ? 'on' : ''} onClick={() => setView('plain')}>
        Plain English
      </button>
      <button aria-pressed={technical} className={technical ? 'on' : ''} onClick={() => setView('technical')}>
        Technical terms
      </button>
    </div>
  );
}
