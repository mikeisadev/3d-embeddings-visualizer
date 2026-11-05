import { useEffect, useId, useRef } from 'react';
import ui from '../../components/ui.module.css';
import { it } from '../../i18n/it';
import styles from './FirstRunOverlay.module.css';

interface FirstRunOverlayProps {
  readonly onDismiss: () => void;
}

/** First-run guide pointing at the three main actions; closed with the button or Escape. */
export function FirstRunOverlay({ onDismiss }: FirstRunOverlayProps) {
  const titleId = useId();
  const buttonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    buttonRef.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onDismiss();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('keydown', onKey);
    };
  }, [onDismiss]);

  return (
    <div className={styles.backdrop}>
      <div className={styles.dialog} role="dialog" aria-modal="true" aria-labelledby={titleId}>
        <h2 id={titleId} className={styles.title}>
          {it.firstRun.title}
        </h2>
        <p>{it.firstRun.intro}</p>
        <ol className={styles.steps}>
          {it.firstRun.steps.map((step) => (
            <li key={step}>{step}</li>
          ))}
        </ol>
        <p className={ui.note}>{it.firstRun.caveat}</p>
        <button ref={buttonRef} type="button" className={ui.primary} onClick={onDismiss}>
          {it.firstRun.dismiss}
        </button>
      </div>
    </div>
  );
}
