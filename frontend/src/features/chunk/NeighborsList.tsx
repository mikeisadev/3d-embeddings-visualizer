import { useId } from 'react';
import type { Neighbors } from '../../api/schemas';
import { AsyncView } from '../../components/AsyncView';
import ui from '../../components/ui.module.css';
import { useNeighbors, usePointCloudView } from '../../hooks/queries';
import { it } from '../../i18n/it';
import { toAsyncState } from '../../lib/asyncState';
import { formatNumber } from '../../lib/format';
import { useUiStore } from '../../state/uiStore';
import styles from './NeighborsList.module.css';

const K_OPTIONS = [5, 10, 20, 50] as const;

/** The real nearest neighbors of the selected chunk, computed by the database. */
export function NeighborsList({ id }: { readonly id: string }) {
  const neighbors = useNeighbors(id);
  const k = useUiStore((state) => state.neighborsK);
  const setK = useUiStore((state) => state.setNeighborsK);
  const selectId = useId();

  return (
    <>
      <p className={ui.muted}>{it.chunk.neighborsHint}</p>
      <div className={ui.row}>
        <label htmlFor={selectId}>{it.chunk.neighborsK}</label>
        <select
          id={selectId}
          value={k}
          onChange={(event) => {
            setK(Number(event.target.value));
          }}
        >
          {K_OPTIONS.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </div>
      <AsyncView
        state={toAsyncState(neighbors)}
        onRetry={() => {
          void neighbors.refetch();
        }}
      >
        {(result) => <NeighborItems result={result} />}
      </AsyncView>
    </>
  );
}

function NeighborItems({ result }: { readonly result: Neighbors }) {
  const view = usePointCloudView();
  const select = useUiStore((state) => state.select);
  const focusOn = useUiStore((state) => state.focusOn);

  return (
    <>
      <ol className={styles.list}>
        {result.neighbors.map((neighbor) => {
          const inView = view?.indexById.has(neighbor.id) ?? false;
          // Until the points are loaded we cannot tell whether the neighbor is in the sample.
          const outsideSample = view !== null && !inView;
          return (
            <li key={neighbor.id} className={styles.item}>
              <div className={styles.header}>
                <span className={styles.similarity}>
                  {it.chunk.similarity}:{' '}
                  {neighbor.similarity === null ? '—' : formatNumber(neighbor.similarity)}
                </span>
                <button
                  type="button"
                  className={ui.linkButton}
                  disabled={!inView}
                  onClick={() => {
                    focusOn(neighbor.id);
                  }}
                >
                  {outsideSample ? it.chunk.notInSample : it.chunk.showInView}
                </button>
              </div>
              <button
                type="button"
                className={styles.body}
                onClick={() => {
                  select(neighbor.id);
                }}
              >
                <span className={ui.muted}>
                  {it.chunk.rank(neighbor.rank)} {neighbor.file} ·{' '}
                  {it.colors.pageShort(neighbor.page)}
                </span>
                <span className={styles.preview}>{neighbor.preview}</span>
              </button>
            </li>
          );
        })}
      </ol>
      <p className={ui.muted}>{it.chunk.exactSearch}</p>
      <details>
        <summary>{it.chunk.showSql}</summary>
        <pre className={ui.code}>{result.sql}</pre>
      </details>
    </>
  );
}
