import { ErrorMessage } from '../../components/ErrorMessage';
import ui from '../../components/ui.module.css';
import { it } from '../../i18n/it';
import { type UmapState, useUiStore } from '../../state/uiStore';
import { progressShare } from '../../umap/progress';
import styles from './ProjectionSection.module.css';

/** Progress, cancellation and errors of the UMAP computation. */
export function UmapStatus() {
  const umap = useUiStore((state) => state.umap);
  const setProjectionMethod = useUiStore((state) => state.setProjectionMethod);
  const retryUmap = useUiStore((state) => state.retryUmap);
  const backToPca = () => {
    setProjectionMethod('pca');
  };

  switch (umap.status) {
    case 'idle':
      return null;
    case 'loading':
    case 'running':
      return (
        <div className={styles.umapStatus} role="status">
          <p>{progressText(umap)}</p>
          {/* Without a value the bar is indeterminate: the neighbor search reports no progress. */}
          <progress
            className={styles.progress}
            max={1}
            value={progressShare(umap) ?? undefined}
            aria-label={it.projection.umapProgress}
          />
          <p className={ui.muted}>{it.projection.umapPcaMeanwhile}</p>
          <button type="button" className={ui.button} onClick={backToPca}>
            {it.projection.umapCancel}
          </button>
        </div>
      );
    case 'done':
      return <p className={ui.muted}>{it.projection.umapReady}</p>;
    case 'error':
      return (
        <div className={styles.umapStatus}>
          <ErrorMessage error={umap.error} onRetry={retryUmap} />
          <button type="button" className={ui.button} onClick={backToPca}>
            {it.projection.methods.pca}
          </button>
        </div>
      );
  }
}

type ActiveUmap = Extract<UmapState, { status: 'loading' | 'running' }>;

function progressText(umap: ActiveUmap): string {
  if (umap.status === 'loading') {
    return it.projection.umapDownloading;
  }
  return umap.progress.phase === 'neighbors'
    ? it.projection.umapNeighbors
    : it.projection.umapOptimizing(umap.progress.epoch, umap.progress.epochs);
}
