import type { ReactNode } from 'react';
import { it } from '../i18n/it';
import type { AsyncState } from '../lib/asyncState';
import { ErrorMessage } from './ErrorMessage';
import ui from './ui.module.css';

interface AsyncViewProps<T> {
  readonly state: AsyncState<T>;
  readonly onRetry?: () => void;
  readonly empty?: ReactNode;
  readonly loading?: ReactNode;
  readonly children: (data: T) => ReactNode;
}

/** Renders the four states of a panel: loading, empty, error (with "Riprova") and success. */
export function AsyncView<T>({ state, onRetry, empty, loading, children }: AsyncViewProps<T>) {
  switch (state.status) {
    case 'loading':
      return (
        <p className={ui.muted} aria-busy="true">
          {loading ?? it.common.loading}
        </p>
      );
    case 'error':
      return <ErrorMessage error={state.error} {...(onRetry ? { onRetry } : {})} />;
    case 'empty':
      return <p className={ui.muted}>{empty}</p>;
    case 'success':
      return <>{children(state.data)}</>;
  }
}
