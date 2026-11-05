import type { Comparison, Point } from '../../api/schemas';
import { AsyncView } from '../../components/AsyncView';
import { Section } from '../../components/Section';
import ui from '../../components/ui.module.css';
import { type PointCloudView, useComparison, usePointCloudView } from '../../hooks/queries';
import { it } from '../../i18n/it';
import { toAsyncState } from '../../lib/asyncState';
import { comparableDistance, shownShare } from '../../lib/compare';
import { distanceBetween } from '../../lib/displayTransform';
import { formatNumber } from '../../lib/format';
import { type CompareSlot, useUiStore } from '../../state/uiStore';
import styles from './CompareSection.module.css';

/** Section 6, "Confronta due chunk": the real similarity next to the distance in the 3D view. */
export function CompareSection() {
  const compareA = useUiStore((state) => state.compareA);
  const compareB = useUiStore((state) => state.compareB);
  const comparison = useComparison(compareA, compareB);
  const view = usePointCloudView();

  return (
    <Section title={it.compare.title} info={it.compare.info} lesson={it.compare.lesson}>
      <SlotPicker slot="A" id={compareA} view={view} />
      <SlotPicker slot="B" id={compareB} view={view} />
      {compareA !== null && compareB !== null && (
        <AsyncView
          state={toAsyncState(comparison)}
          onRetry={() => {
            void comparison.refetch();
          }}
        >
          {(result) => <ComparisonView comparison={result} view={view} />}
        </AsyncView>
      )}
    </Section>
  );
}

interface SlotPickerProps {
  readonly slot: CompareSlot;
  readonly id: string | null;
  readonly view: PointCloudView | null;
}

function SlotPicker({ slot, id, view }: SlotPickerProps) {
  const selectedId = useUiStore((state) => state.selectedId);
  const setCompare = useUiStore((state) => state.setCompare);
  const point = id === null ? undefined : pointOf(view, id);

  return (
    <div className={styles.slot}>
      <span className={styles.badge}>{slot === 'A' ? it.compare.slotA : it.compare.slotB}</span>
      <div className={styles.slotBody}>
        {id === null ? (
          <span className={ui.muted}>{it.compare.emptySlot}</span>
        ) : (
          <>
            <span className={ui.muted}>
              {point === undefined ? id : `${point.file} · ${it.colors.pageShort(point.page)}`}
            </span>
            {point !== undefined && <span className={styles.preview}>{point.preview}</span>}
          </>
        )}
        <div className={ui.row}>
          <button
            type="button"
            className={ui.button}
            disabled={selectedId === null}
            onClick={() => {
              setCompare(slot, selectedId);
            }}
          >
            {it.compare.pickSelected}
          </button>
          {id !== null && (
            <button
              type="button"
              className={ui.button}
              onClick={() => {
                setCompare(slot, null);
              }}
            >
              {it.compare.clear}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

function ComparisonView({
  comparison,
  view,
}: {
  readonly comparison: Comparison;
  readonly view: PointCloudView | null;
}) {
  const umap = view?.method === 'umap';
  const projected = umap ? null : projectedDistance(view, comparison.a, comparison.b);
  const normalized = view?.data.projection.normalized ?? true;
  const real = comparableDistance(comparison, normalized);
  const share = projected === null ? null : shownShare(projected, real);

  return (
    <div className={styles.result}>
      {comparison.a === comparison.b && <p className={ui.muted}>{it.compare.sameChunk}</p>}
      <div>
        <div>
          <h3 className={ui.subheading}>{it.compare.real}</h3>
          <dl className={ui.facts}>
            <dt>{it.compare.cosineSimilarity}</dt>
            <dd className={styles.big}>{format(comparison.cosineSimilarity)}</dd>
            <dt>{it.compare.cosineDistance}</dt>
            <dd>{format(comparison.cosineDistance)}</dd>
            <dt>{it.compare.euclideanDistance}</dt>
            <dd>{format(real)}</dd>
            <dt>{it.compare.dotProduct}</dt>
            <dd>{format(comparison.dotProduct)}</dd>
          </dl>
        </div>
        <div>
          <h3 className={ui.subheading}>{it.compare.projected}</h3>
          {umap ? (
            <p className={ui.muted}>{it.compare.umapDistance}</p>
          ) : (
            <dl className={ui.facts}>
              <dt>{it.compare.distance3d}</dt>
              <dd className={styles.big}>{format(projected)}</dd>
            </dl>
          )}
        </div>
      </div>
      {!umap &&
        (projected === null ? (
          <p className={ui.warningNote}>{it.compare.notInView}</p>
        ) : (
          share !== null && <p>{it.compare.shownShare(share)}</p>
        ))}
      <p className={ui.note}>{it.compare.why}</p>
    </div>
  );
}

/** Distance of the two chunks in the PCA coordinates (the units of the vectors), when both are on screen. */
function projectedDistance(view: PointCloudView | null, a: string, b: string): number | null {
  const indexA = view?.indexById.get(a);
  const indexB = view?.indexById.get(b);
  if (view === null || indexA === undefined || indexB === undefined) {
    return null;
  }
  return distanceBetween(view.coordinates, indexA, indexB);
}

function pointOf(view: PointCloudView | null, id: string): Point | undefined {
  const index = view?.indexById.get(id);
  return index === undefined ? undefined : view?.data.points[index];
}

function format(value: number | null): string {
  return value === null ? '—' : formatNumber(value);
}
