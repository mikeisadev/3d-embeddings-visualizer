import { it } from '../i18n/it';
import { describeError } from '../lib/errorMessages';
import styles from './ErrorMessage.module.css';
import ui from './ui.module.css';

interface ErrorMessageProps {
  readonly error: unknown;
  readonly onRetry?: () => void;
}

/** An error as the student sees it: what happened, what to do, and the codes to report. */
export function ErrorMessage({ error, onRetry }: ErrorMessageProps) {
  const described = describeError(error);
  return (
    <div className={styles.error} role="alert">
      <p className={styles.message}>{described.message}</p>
      <p className={styles.hint}>{described.hint}</p>
      <p className={styles.codes}>
        {it.common.errorCode}: <code>{described.code}</code>
        {described.requestId !== null && (
          <>
            {' · '}
            {it.common.requestId}: <code>{described.requestId}</code>
          </>
        )}
      </p>
      {described.detail !== null && (
        <details className={styles.detail}>
          <summary>{it.common.technicalDetail}</summary>
          <p>{described.detail}</p>
        </details>
      )}
      {onRetry && (
        <button type="button" className={ui.button} onClick={onRetry}>
          {it.common.retry}
        </button>
      )}
    </div>
  );
}
