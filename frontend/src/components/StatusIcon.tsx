import type { CheckStatus } from '../api/schemas';
import { it } from '../i18n/it';
import styles from './StatusIcon.module.css';

const SYMBOLS: Record<CheckStatus, string> = { OK: '✓', WARN: '!', FAIL: '✕', SKIPPED: '–' };

/** Status symbol with a text alternative; shape and color both carry the meaning. */
export function StatusIcon({ status }: { readonly status: CheckStatus }) {
  return (
    <span
      className={`${styles.icon} ${styles[status]}`}
      role="img"
      aria-label={it.status.statusLabel[status]}
    >
      {SYMBOLS[status]}
    </span>
  );
}
