import { useId } from 'react';
import type { Projection } from '../../api/schemas';
import { AsyncView } from '../../components/AsyncView';
import { Section } from '../../components/Section';
import ui from '../../components/ui.module.css';
import { useDataset, usePoints } from '../../hooks/queries';
import { it } from '../../i18n/it';
import { toAsyncState } from '../../lib/asyncState';
import { formatNumber, formatPercent } from '../../lib/format';
import { useUiStore } from '../../state/uiStore';
import styles from './ProjectionSection.module.css';

/** Section 3, "Proiezione": method, explained variance, normalization and point size. */
export function ProjectionSection() {
  const points = usePoints();
  const metric = useDataset().data?.distanceMetric.metric;
  const normalize = useUiStore((state) => state.normalize);
  const setNormalize = useUiStore((state) => state.setNormalize);
  const pointSize = useUiStore((state) => state.pointSize);
  const setPointSize = useUiStore((state) => state.setPointSize);
  const normalizeId = useId();
  const sizeId = useId();
  const cosine = metric === undefined || metric === 'COSINE';

  return (
    <Section title={it.projection.title} info={it.projection.info} lesson={it.projection.lesson}>
      <dl className={ui.facts}>
        <dt>{it.projection.method}</dt>
        <dd>{it.projection.pca}</dd>
      </dl>

      <h3 className={ui.subheading}>{it.projection.explained}</h3>
      <AsyncView
        state={toAsyncState(points)}
        onRetry={() => {
          void points.refetch();
        }}
      >
        {(cloud) => <VarianceBars projection={cloud.projection} />}
      </AsyncView>

      <div className={styles.control}>
        <label htmlFor={normalizeId} className={styles.checkbox}>
          <input
            id={normalizeId}
            type="checkbox"
            checked={normalize && cosine}
            disabled={!cosine}
            onChange={(event) => {
              setNormalize(event.target.checked);
            }}
          />
          {it.projection.normalize}
        </label>
        <p className={ui.muted}>
          {cosine ? it.projection.normalizeHint : it.projection.normalizeOnlyCosine}
        </p>
      </div>

      <div className={styles.control}>
        <label htmlFor={sizeId} className={ui.label}>
          {it.projection.pointSize}: {formatNumber(pointSize, 1)}×
        </label>
        <input
          id={sizeId}
          type="range"
          min={0.5}
          max={3}
          step={0.25}
          value={pointSize}
          onChange={(event) => {
            setPointSize(Number(event.target.value));
          }}
        />
      </div>

      <p className={ui.note}>{it.projection.note}</p>
    </Section>
  );
}

function VarianceBars({ projection }: { readonly projection: Projection }) {
  return (
    <>
      <ul className={styles.bars}>
        {projection.explainedVarianceRatio.map((ratio, index) => (
          <li key={index} className={styles.bar}>
            <span className={styles.barLabel}>{it.projection.component(index)}</span>
            <meter
              className={styles.meter}
              min={0}
              max={1}
              value={ratio}
              aria-label={it.projection.component(index)}
            />
            <span className={styles.barValue}>{formatPercent(ratio)}</span>
          </li>
        ))}
        <li className={styles.bar}>
          <span className={styles.barLabel}>{it.projection.total}</span>
          <meter
            className={styles.meter}
            min={0}
            max={1}
            value={projection.totalExplainedVariance}
            aria-label={it.projection.total}
          />
          <span className={styles.barValue}>
            {formatPercent(projection.totalExplainedVariance)}
          </span>
        </li>
      </ul>
      <p>{it.projection.totalExplained(projection.totalExplainedVariance)}</p>
      {!projection.converged && <p className={ui.warningNote}>{it.projection.notConverged}</p>}
    </>
  );
}
