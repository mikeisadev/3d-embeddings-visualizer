import { useMemo } from 'react';
import { AsyncView } from '../../components/AsyncView';
import ui from '../../components/ui.module.css';
import { useNeighborSample, usePointCloudView } from '../../hooks/queries';
import { it } from '../../i18n/it';
import { toAsyncState } from '../../lib/asyncState';
import { projectionFidelity } from '../../lib/fidelity';
import { formatPercent } from '../../lib/format';
import styles from './ProjectionSection.module.css';

/**
 * "Fedeltà della proiezione": how many real neighbors (backend, full vectors) are also
 * neighbors on screen (3 coordinates). Recomputed for PCA, UMAP and the normalize flag.
 */
export function FidelityScore() {
  const view = usePointCloudView();
  const sample = useNeighborSample();
  const sampleData = sample.data;
  const score = useMemo(() => {
    if (view === null || sampleData?.datasetVersion !== view.data.datasetVersion) {
      return undefined;
    }
    return projectionFidelity(view.coordinates, view.indexById, sampleData);
  }, [view, sampleData]);

  return (
    <>
      <h3 className={ui.subheading}>{it.projection.fidelity}</h3>
      <AsyncView
        state={toAsyncState(sample)}
        onRetry={() => {
          void sample.refetch();
        }}
      >
        {() => {
          if (score === undefined) {
            return <p className={ui.muted}>{it.common.loading}</p>;
          }
          if (score === null) {
            return <p className={ui.muted}>{it.projection.fidelityUnavailable}</p>;
          }
          return (
            <>
              <div className={styles.bar}>
                <span className={styles.barLabel}>k = {score.k}</span>
                <meter
                  className={styles.meter}
                  min={0}
                  max={1}
                  value={score.share}
                  aria-label={it.projection.fidelity}
                />
                <span className={styles.barValue}>{formatPercent(score.share)}</span>
              </div>
              <p>{it.projection.fidelityExplained(score.meanKept, score.k, score.samples)}</p>
              <p className={ui.muted}>{it.projection.fidelityNote}</p>
            </>
          );
        }}
      </AsyncView>
    </>
  );
}
